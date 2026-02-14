package com.dsip.backend.service;

import com.dsip.backend.dto.CompanyDetails;
import com.dsip.backend.dto.StockPriceResponse;
import com.dsip.backend.entity.Exchange;
import com.dsip.backend.entity.Stock;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.exception.StockPriceFetchException;
import com.dsip.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockMapper stockMapper;
    private final FinnhubService finnhubService;
    private final UpstoxService upstoxService;

    @Transactional
    public StockPriceResponse getClosingPrice(String symbol, Exchange exchange) {
        if (exchange != Exchange.US) {
            throw new IllegalArgumentException(
                    "Currently only the US market is supported. Support for " + exchange
                            + " and other markets will be available in a future update.");
        }

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        log.info("Request received for stock: {}, exchange: {}, todayUTC: {}", symbol, exchange, todayUtc);

        // Check DB cache by symbol AND today's UTC date
        Optional<Stock> cachedStock = stockMapper.findByStockSymbolAndDate(symbol, todayUtc);

        if (cachedStock.isPresent()) {
            log.info("CACHE HIT: Stock {} found in DB for today (UTC). Skipping external API call.", symbol);
            Stock stock = cachedStock.get();

            CompanyDetails companyDetails = CompanyDetails.builder()
                    .name(stock.getStockName())
                    .symbol(stock.getStockSymbol())
                    .exchange(stock.getListedExchange().name())
                    .industry("N/A")
                    .country(stock.getListedExchange() == Exchange.US ? "US" : "India")
                    .build();

            return StockPriceResponse.builder()
                    .symbol(stock.getStockSymbol())
                    .exchange(stock.getListedExchange().name())
                    .date(todayUtc.toString())
                    .closePrice(stock.getLastDateMarketClosingPrice())
                    .source("DB")
                    .company(companyDetails)
                    .build();
        }

        // CACHE MISS: stock not in DB for today
        log.info("CACHE MISS: Stock {} not found for today. Calling external API...", symbol);

        Double closePrice;
        CompanyDetails companyDetails;

        if (exchange == Exchange.US) {
            closePrice = finnhubService.fetchClosingPrice(symbol);
            companyDetails = finnhubService.fetchCompanyDetails(symbol);
        } else {
            closePrice = upstoxService.fetchClosingPrice(symbol, exchange.name());
            companyDetails = upstoxService.fetchCompanyDetails(symbol, exchange.name());
        }

        if (closePrice == null) {
            log.error("Failed to fetch closing price from API for symbol: {}", symbol);
            throw new StockPriceFetchException(symbol,
                    "Stock '" + symbol + "' not found or price data is unavailable. "
                            + "Please verify the symbol is a valid US market stock.");
        }

        Instant nowUtc = Instant.now();

        // Check if stock exists in DB (stale entry) — update it; otherwise insert
        Optional<Stock> existingStock = stockMapper.findByStockSymbol(symbol);

        if (existingStock.isPresent()) {
            Stock stock = existingStock.get();
            stock.setStockName(companyDetails.getName());
            stock.setListedExchange(exchange);
            stock.setLastDateMarketClosingPrice(closePrice);
            stock.setLastUpdatedDate(nowUtc);
            if (stock.getStockType() == null) {
                stock.setStockType(StockType.PENNY);
            }
            stockMapper.update(stock);
            log.info("Stock {} updated in DB cache.", symbol);
        } else {
            Stock newStock = Stock.builder()
                    .stockSymbol(symbol)
                    .stockName(companyDetails.getName())
                    .listedExchange(exchange)
                    .stockType(StockType.PENNY)
                    .lastDateMarketClosingPrice(closePrice)
                    .lastUpdatedDate(nowUtc)
                    .build();
            stockMapper.insert(newStock);
            log.info("Stock {} saved to DB cache.", symbol);
        }

        return StockPriceResponse.builder()
                .symbol(symbol)
                .exchange(exchange.name())
                .date(todayUtc.toString())
                .closePrice(closePrice)
                .source("API")
                .company(companyDetails)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isStockCached(String symbol) {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        return stockMapper.findByStockSymbolAndDate(symbol, todayUtc).isPresent();
    }

    @Transactional
    public boolean removeFromCache(String symbol) {
        if (stockMapper.existsByStockSymbol(symbol)) {
            stockMapper.deleteByStockSymbol(symbol);
            log.info("Stock {} removed from cache.", symbol);
            return true;
        }
        return false;
    }
}
