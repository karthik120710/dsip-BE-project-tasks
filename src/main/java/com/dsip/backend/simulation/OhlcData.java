package com.dsip.backend.simulation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Represents a single day's OHLC (Open-High-Low-Close) price data.
 */
@Data
@Builder
public class OhlcData {

    /**
     * Trading date.
     */
    private LocalDate date;

    /**
     * Opening price.
     */
    private double open;

    /**
     * Highest price of the day.
     */
    private double high;

    /**
     * Lowest price of the day.
     */
    private double low;

    /**
     * Closing price.
     */
    private double close;

    /**
     * Trading volume (optional).
     */
    private Long volume;

    /**
     * Calculate daily price change percentage (close vs previous close).
     */
    public double calculateChangePercent(double previousClose) {
        if (previousClose <= 0) return 0;
        return ((close - previousClose) / previousClose) * 100;
    }

    /**
     * Calculate intraday range percentage.
     */
    public double calculateIntradayRangePercent() {
        if (low <= 0) return 0;
        return ((high - low) / low) * 100;
    }

    /**
     * Calculate gap percentage (open vs previous close).
     */
    public double calculateGapPercent(double previousClose) {
        if (previousClose <= 0) return 0;
        return ((open - previousClose) / previousClose) * 100;
    }
}
