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
                    .date(stock.getLastUpdatedDate().toString())
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

        StockType resolvedType = resolveStockType(companyDetails);

        if (existingStock.isPresent()) {
            Stock stock = existingStock.get();
            stock.setStockName(companyDetails.getName());
            stock.setListedExchange(exchange);
            stock.setLastDateMarketClosingPrice(closePrice);
            stock.setLastUpdatedDate(nowUtc);
            stock.setStockType(resolvedType);
            stockMapper.update(stock);
            log.info("Stock {} updated in DB cache with type {}.", symbol, resolvedType);
        } else {
            Stock newStock = Stock.builder()
                    .stockSymbol(symbol)
                    .stockName(companyDetails.getName())
                    .listedExchange(exchange)
                    .stockType(resolvedType)
                    .lastDateMarketClosingPrice(closePrice)
                    .lastUpdatedDate(nowUtc)
                    .build();
            stockMapper.insert(newStock);
            log.info("Stock {} saved to DB cache with type {}.", symbol, resolvedType);
        }

        return StockPriceResponse.builder()
                .symbol(symbol)
                .exchange(exchange.name())
                .date(nowUtc.toString())
                .closePrice(closePrice)
                .source("API")
                .company(companyDetails)
                .build();
    }

    private StockType resolveStockType(CompanyDetails companyDetails) {
        Double marketCap = companyDetails.getMarketCapitalization();
        if (marketCap != null && marketCap > 0) {
            StockType type = StockType.fromMarketCap(marketCap);
            log.info("Resolved stock type for {}: {} (marketCap={}M)", companyDetails.getSymbol(), type, marketCap);
            return type;
        }
        log.warn("Market cap unavailable for {}, defaulting to PENNY", companyDetails.getSymbol());
        return StockType.PENNY;
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
