package com.dsip.backend.controller;

import com.dsip.backend.dto.StockPriceResponse;
import com.dsip.backend.entity.Exchange;
import com.dsip.backend.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST controller for stock price operations.
 * Provides endpoint to fetch daily closing stock prices with DB caching.
 */
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final StockService stockService;

    /**
     * Fetches the closing price for a stock on a specific date.
     * Data is first checked in DB cache. External API is called only if not cached.
     *
     * Example requests:
     * - US stock: GET /api/stocks/close?symbol=AAPL&exchange=US&date=2024-01-10
     * - Indian stock: GET /api/stocks/close?symbol=RELIANCE&exchange=NSE&date=2024-01-10
     *
     * @param symbol stock symbol (e.g., "AAPL", "RELIANCE")
     * @param exchange exchange (US, NSE, or BSE)
     * @param date date in YYYY-MM-DD format
     * @return StockPriceResponse with closing price, source, and company details
     */
    @GetMapping("/close")
    public ResponseEntity<StockPriceResponse> getClosingPrice(
            @RequestParam String symbol,
            @RequestParam Exchange exchange,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("API request: GET /api/stocks/close - symbol={}, exchange={}, date={}",
                 symbol, exchange, date);

        try {
            // Delegate to service layer
            StockPriceResponse response = stockService.getClosingPrice(symbol, exchange, date);

            log.info("API response: symbol={}, closePrice={}, source={}",
                     response.getSymbol(), response.getClosePrice(), response.getSource());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing request for symbol: {}, exchange: {}, date: {}",
                      symbol, exchange, date, e);
            throw e;
        }
    }

    /**
     * Utility endpoint to check if a stock is cached in DB.
     * Useful for testing and debugging.
     *
     * Example: GET /api/stocks/cached?symbol=AAPL
     *
     * @param symbol stock symbol
     * @return JSON response with cached status
     */
    @GetMapping("/cached")
    public ResponseEntity<Map<String, Object>> isCached(@RequestParam String symbol) {
        boolean cached = stockService.isStockCached(symbol);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "cached", cached
        ));
    }

    /**
     * Utility endpoint to remove a stock from cache.
     * Useful for testing and cache invalidation.
     *
     * Example: DELETE /api/stocks/cache?symbol=AAPL
     *
     * @param symbol stock symbol
     * @return JSON response with deletion status
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, Object>> removeFromCache(@RequestParam String symbol) {
        boolean removed = stockService.removeFromCache(symbol);

        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "symbol", symbol,
                    "message", "Stock removed from cache",
                    "status", "success"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "symbol", symbol,
                    "message", "Stock not found in cache",
                    "status", "not_found"
            ));
        }
    }
}
