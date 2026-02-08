package com.dsip.backend.exception;

public class StockPriceFetchException extends DsipException {
    public StockPriceFetchException(String symbol, String reason) {
        super(ErrorCode.STOCK_PRICE_FETCH_FAILED, "Failed to fetch price for " + symbol + ": " + reason);
    }

    public StockPriceFetchException(String symbol, String reason, Throwable cause) {
        super(ErrorCode.STOCK_PRICE_FETCH_FAILED, "Failed to fetch price for " + symbol + ": " + reason, cause);
    }
}
