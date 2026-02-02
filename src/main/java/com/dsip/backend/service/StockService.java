package com.dsip.backend.service;

import com.dsip.backend.dto.CompanyDetails;
import com.dsip.backend.dto.StockPriceResponse;
import com.dsip.backend.entity.Exchange;
import com.dsip.backend.entity.Stock;
import com.dsip.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Orchestrator service for stock price retrieval.
 * Implements DB-first caching strategy:
 * 1. Check if stock exists in DB
 * 2. If YES → return from DB (no API call)
 * 3. If NO → fetch from external API, store in DB, return response
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockMapper stockMapper;
    private final FinnhubService finnhubService;
    private final UpstoxService upstoxService;

    /**
     * CORE METHOD: Fetches closing price for a stock on a given date.
     * ALWAYS checks DB first. External API is called ONLY if stock is not in cache.
     *
     * @param symbol stock symbol (e.g., "AAPL", "RELIANCE")
     * @param exchange exchange (US, NSE, or BSE)
     * @param date date to fetch closing price for
     * @return StockPriceResponse with price, source, and company details
     */
    @Transactional
    public StockPriceResponse getClosingPrice(String symbol, Exchange exchange, LocalDate date) {
        log.info("Request received for stock: {}, exchange: {}, date: {}", symbol, exchange, date);

        // STEP 1: Check DB cache by stock symbol
        Optional<Stock> cachedStock = stockMapper.findByStockSymbol(symbol);

        if (cachedStock.isPresent()) {
            // CACHE HIT: Return data from DB without calling external API
            log.info("✓ CACHE HIT: Stock {} found in DB. Skipping external API call.", symbol);
            Stock stock = cachedStock.get();

            // Build company details from cached data
            CompanyDetails companyDetails = CompanyDetails.builder()
                    .name(stock.getStockName())
                    .symbol(stock.getStockSymbol())
                    .exchange(stock.getListedExchange().name())
                    .industry("N/A") // Not stored in DB for simplicity
                    .country(stock.getListedExchange() == Exchange.US ? "US" : "India")
                    .build();

            return StockPriceResponse.builder()
                    .symbol(stock.getStockSymbol())
                    .exchange(stock.getListedExchange().name())
                    .date(stock.getLastUpdatedDate().toString())
                    .closePrice(stock.getLastDateMarketClosingPrice())
                    .source("DB") // Data from database cache
                    .company(companyDetails)
                    .build();
        }

        // CACHE MISS: Stock not in DB, fetch from external API
        log.info("✗ CACHE MISS: Stock {} not found in DB. Calling external API...", symbol);

        Double closePrice;
        CompanyDetails companyDetails;

        if (exchange == Exchange.US) {
            // Fetch from Finnhub API
            closePrice = finnhubService.fetchClosingPrice(symbol, date);
            companyDetails = finnhubService.fetchCompanyDetails(symbol);
        } else {
            // Fetch from Upstox API (NSE or BSE)
            closePrice = upstoxService.fetchClosingPrice(symbol, exchange.name(), date);
            companyDetails = upstoxService.fetchCompanyDetails(symbol, exchange.name());
        }

        if (closePrice == null) {
            log.error("Failed to fetch closing price from API for symbol: {}", symbol);
            throw new RuntimeException("Unable to fetch closing price for " + symbol + " on " + date);
        }

        // STEP 2: Store fetched data in DB for future requests
        Stock newStock = Stock.builder()
                .stockSymbol(symbol)
                .stockName(companyDetails.getName())
                .listedExchange(exchange)
                .lastDateMarketClosingPrice(closePrice)
                .lastUpdatedDate(date)
                .build();

        stockMapper.insert(newStock);
        log.info("✓ Stock {} saved to DB cache for future requests.", symbol);

        // STEP 3: Return response with API source
        return StockPriceResponse.builder()
                .symbol(symbol)
                .exchange(exchange.name())
                .date(date.toString())
                .closePrice(closePrice)
                .source("API") // Data from external API
                .company(companyDetails)
                .build();
    }

    /**
     * Validates if a stock exists in the cache.
     *
     * @param symbol stock symbol
     * @return true if stock exists in DB, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isStockCached(String symbol) {
        return stockMapper.existsByStockSymbol(symbol);
    }

    /**
     * Manually removes a stock from cache (for testing or cache invalidation).
     *
     * @param symbol stock symbol
     * @return true if deleted, false if not found
     */
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
