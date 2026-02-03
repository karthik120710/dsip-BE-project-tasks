package com.dsip.backend.exception;

public class StockPriceFetchException extends RuntimeException {
    public StockPriceFetchException(String symbol, String reason) {
        super("Failed to fetch price for " + symbol + ": " + reason);
    }

    public StockPriceFetchException(String symbol, String reason, Throwable cause) {
        super("Failed to fetch price for " + symbol + ": " + reason, cause);
    }
}
