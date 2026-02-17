package com.dsip.backend.entity;

/**
 * Enum representing stock exchanges.
 * US - United States market (uses Finnhub API)
 * NSE - National Stock Exchange of India (uses Upstox API)
 * BSE - Bombay Stock Exchange of India (uses Upstox API)
 */
public enum Exchange {
    US,
    NSE,
    BSE
}
