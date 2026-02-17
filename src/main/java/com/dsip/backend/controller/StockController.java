package com.dsip.backend.controller;

import com.dsip.backend.dto.StockPriceResponse;
import com.dsip.backend.entity.Exchange;
import com.dsip.backend.exception.StockNotFoundException;
import com.dsip.backend.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockController {

    private final StockService stockService;

    /**
     * Fetches the latest closing price for a stock.
     * Data is cached per day (UTC). External API is called only if not cached today.
     *
     * Example: GET /api/stocks/close?symbol=AAPL&exchange=US
     */
    @GetMapping("/close")
    public ResponseEntity<StockPriceResponse> getClosingPrice(
            @RequestParam String symbol,
            @RequestParam Exchange exchange) {

        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Stock symbol must not be blank");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        log.info("API request: GET /api/stocks/close - symbol={}, exchange={}", normalizedSymbol, exchange);

        StockPriceResponse response = stockService.getClosingPrice(normalizedSymbol, exchange);

        log.info("API response: symbol={}, closePrice={}, source={}",
                 response.getSymbol(), response.getClosePrice(), response.getSource());

        return ResponseEntity.ok(response);
    }

    /**
     * Check if a stock is cached in DB for today (UTC).
     *
     * Example: GET /api/stocks/cached?symbol=AAPL
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
     * Remove a stock from cache.
     *
     * Example: DELETE /api/stocks/cache?symbol=AAPL
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, Object>> removeFromCache(@RequestParam String symbol) {
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Stock symbol must not be blank");
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        boolean removed = stockService.removeFromCache(normalizedSymbol);

        if (!removed) {
            throw new StockNotFoundException(normalizedSymbol);
        }

        return ResponseEntity.ok(Map.of(
                "symbol", normalizedSymbol,
                "message", "Stock removed from cache",
                "status", "success"
        ));
    }
}
