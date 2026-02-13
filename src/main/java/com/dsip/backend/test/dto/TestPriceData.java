package com.dsip.backend.test.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Represents a single day's price data from the test CSV.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestPriceData {
    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double prevClose;
    private double lockInPct;
    private double executedPrice;
    private int convictionScore;
    private double executedAmount;

    /**
     * Determine if this is a red day (market opens below previous close).
     */
    public boolean isRedDay() {
        return open < prevClose;
    }
}
