package com.dsip.backend.simulation;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Top-level result for a batch simulation run across multiple stocks.
 */
@Data
@Builder
public class BatchSimulationResult {
    private int totalStocks;
    private int successCount;
    private int failureCount;
    private List<StockSimulationSummary> results;

    @Data
    @Builder
    public static class StockSimulationSummary {
        private String symbol;
        private boolean success;
        private String error;
        private Integer trackerId;
        private String executionLogCsvPath;
        private int daysSimulated;
        private int partitionsCreated;
        private double overallReturnPct;
        private double totalCapitalInvested;
        private double finalPortfolioValue;
    }
}
