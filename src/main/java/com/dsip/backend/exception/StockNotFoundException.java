package com.dsip.backend.exception;

public class StockNotFoundException extends DsipException {
    public StockNotFoundException(String symbol) {
        super(ErrorCode.STOCK_NOT_FOUND, "Stock not found: " + symbol);
    }
}
