package com.dsip.backend.simulation;

import com.dsip.backend.enums.StockType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Configuration for running a DSIP simulation.
 */
@Data
@Builder
public class SimulationConfig {

    /**
     * Total capital to invest over the conviction period.
     */
    private double totalCapital;

    /**
     * Conviction period in years.
     */
    private double convictionPeriodYears;

    /**
     * Stock type for target return calculation.
     */
    private StockType stockType;

    /**
     * Base conviction score (0-100).
     */
    private int baseConvictionScore;

    /**
     * Deployment style: AGGRESSIVE, MODERATE, or GRADUAL.
     */
    @Builder.Default
    private String deploymentStyle = "MODERATE";

    /**
     * Initial stock price at simulation start (used for generated scenarios).
     */
    private double initialPrice;

    /**
     * Number of partitions to simulate.
     */
    @Builder.Default
    private int maxPartitions = 10;

    /**
     * Expected partition length in trading days.
     */
    @Builder.Default
    private int expectedPartitionDays = 60;

    /**
     * Price scenario to simulate.
     * Options: RANDOM, BULL, BEAR, VOLATILE, SIDEWAYS, CUSTOM, HISTORICAL
     */
    @Builder.Default
    private PriceScenario scenario = PriceScenario.RANDOM;

    /**
     * Custom daily price changes (percentage) when scenario is CUSTOM.
     */
    private List<Double> customPriceChanges;

    /**
     * Historical OHLC data when scenario is HISTORICAL.
     * Takes precedence over generated prices.
     */
    private List<OhlcData> historicalData;

    /**
     * Path to CSV file with historical OHLC data.
     */
    private String csvFilePath;

    /**
     * Seed for random number generation (for reproducibility).
     */
    private Long randomSeed;

    /**
     * Price to use for execution when using OHLC data.
     */
    @Builder.Default
    private ExecutionPriceType executionPriceType = ExecutionPriceType.CLOSE;

    /**
     * Price to use for lock-in calculation.
     * PREV_CLOSE: Compare today's price to yesterday's close (default)
     * OPEN: Compare today's close to today's open
     */
    @Builder.Default
    private LockInReferenceType lockInReferenceType = LockInReferenceType.PREV_CLOSE;

    public enum PriceScenario {
        RANDOM,      // Random price movements within typical range
        BULL,        // Trending upward (avg +0.15% daily)
        BEAR,        // Trending downward (avg -0.15% daily)
        VOLATILE,    // High volatility (large swings)
        SIDEWAYS,    // Minimal price movement
        CUSTOM,      // Use customPriceChanges list
        HISTORICAL   // Use historicalData OHLC list
    }

    public enum ExecutionPriceType {
        OPEN,    // Execute at open price
        CLOSE,   // Execute at close price
        VWAP,    // Approximate VWAP as (high + low + close) / 3
        LOW,     // Execute at low (best case)
        HIGH     // Execute at high (worst case)
    }

    public enum LockInReferenceType {
        PREV_CLOSE,  // Compare to previous day's close
        OPEN         // Compare to today's open
    }

    /**
     * Check if using historical data.
     */
    public boolean isHistoricalMode() {
        return scenario == PriceScenario.HISTORICAL
                || historicalData != null
                || csvFilePath != null;
    }
}
