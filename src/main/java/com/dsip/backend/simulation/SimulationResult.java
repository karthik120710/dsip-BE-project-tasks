package com.dsip.backend.simulation;

import com.dsip.backend.enums.EndReason;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Result of a DSIP simulation run.
 */
@Data
@Builder
public class SimulationResult {

    /**
     * Configuration used for this simulation.
     */
    private SimulationConfig config;

    /**
     * Total trading days simulated.
     */
    private int totalDaysSimulated;

    /**
     * Number of partitions created.
     */
    private int partitionsCreated;

    /**
     * Number of successful partitions.
     */
    private int successfulPartitions;

    /**
     * Number of killed partitions.
     */
    private int killedPartitions;

    /**
     * Number of neutral partitions.
     */
    private int neutralPartitions;

    /**
     * Total capital invested across all partitions.
     */
    private double totalCapitalInvested;

    /**
     * Total shares accumulated.
     */
    private double totalSharesAccumulated;

    /**
     * Average price paid per share.
     */
    private double averagePricePaid;

    /**
     * Final market price at simulation end.
     */
    private double finalMarketPrice;

    /**
     * Portfolio value at simulation end.
     */
    private double finalPortfolioValue;

    /**
     * Overall return percentage.
     */
    private double overallReturnPct;

    /**
     * Details for each partition.
     */
    private List<PartitionResult> partitionResults;

    /**
     * Daily execution details.
     */
    private List<DayResult> dayResults;

    /**
     * Result summary for a single partition.
     */
    @Data
    @Builder
    public static class PartitionResult {
        private int partitionIndex;
        private double capitalAllocated;
        private double capitalInvested;
        private double sharesAccumulated;
        private double avgPrice;
        private int daysActive;
        private int successfulGrowthDays;
        private double cumulativeReturnPct;
        private EndReason endReason;
        private double partitionProgressPct;
        private double timeProgressPct;
        private double capitalProgressPct;
    }

    /**
     * Details for a single trading day with all calculation breakdowns.
     * This captures every intermediate value for verification purposes.
     */
    @Data
    @Builder
    public static class DayResult {
        // === Basic Info ===
        private int dayNumber;
        private int partitionIndex;
        private LocalDate date;

        // === Price Data ===
        private double openPrice;
        private Double highPrice;
        private Double lowPrice;
        private double closePrice;
        private double executionPrice;
        private double lockInReferencePrice;

        // === Price Changes ===
        private double priceChangePct;          // (close - prevClose) / prevClose * 100
        private double lockInPct;               // (execPrice - lockInRef) / lockInRef * 100

        // === Opportunity Multiplier Breakdown ===
        private double avgHoldingPrice;         // capitalInvested / shares
        private double avgDeviationPct;         // (avgHolding - execPrice) / avgHolding * 100
        private double avgSignal;               // clamp(avgDeviation / 10, -1, 1)
        private double lockInSignal;            // clamp(lockInPct / 8, -1, 1)
        private double rawOpportunitySignal;    // 0.65 * avgSignal - 0.35 * lockInSignal
        private double convictionAmplifier;     // 0.6 + (score/100) * 0.6
        private boolean isAbnormalDip;          // Detected abnormal dip
        private double opportunityMultiplier;   // Final opportunity multiplier

        // === Contingency Multiplier Breakdown ===
        private double returnProgress;          // cumulativeReturn / targetReturn
        private double growthProgress;          // growthDays / expectedDays
        private double partitionProgress;       // 0.8 * returnProgress + 0.2 * growthProgress
        private double contingencyMultiplier;   // 1.1 - (partitionProgress * 0.4), clamped

        // === Investment Calculation ===
        private double neutralCapital;          // allocatedCapital / expectedDays
        private double finalMultiplier;         // opportunityMult * contingencyMult
        private double recommendedAmount;       // neutralCapital * finalMultiplier
        private double executedAmount;          // min(recommended, remaining)
        private double sharesAcquired;          // executedAmount / executionPrice

        // === Running Totals (Partition Level) ===
        private double partitionCapitalInvested;
        private double partitionSharesBought;
        private double partitionRemainingCapital;

        // === Running Totals (Tracker Level) ===
        private double totalCapitalInvested;
        private double totalSharesHeld;
        private double portfolioValue;          // totalShares * closePrice

        // === Performance Metrics ===
        private double cumulativeReturnPct;     // ((closePrice - avgPrice) / avgPrice) * 100
        private boolean isGrowthDay;            // closePrice > prevClose && return > 0
        private int growthDayCount;             // Running count

        // === Progress Metrics ===
        private double timeProgressPct;         // daysElapsed / expectedDays * 100
        private double capitalProgressPct;      // capitalInvested / capitalAllocated * 100
        private double partitionProgressPct;    // Overall partition progress * 100

        // === Partition End Info (if applicable) ===
        private String partitionEndReason;      // null if partition continues

        // === Notes ===
        private String notes;
    }

    /**
     * Generate a summary report of the simulation.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== DSIP SIMULATION REPORT ==========\n\n");

        // Configuration Summary
        sb.append("--- Configuration ---\n");
        sb.append(String.format("Total Capital: $%.2f%n", config.getTotalCapital()));
        sb.append(String.format("Conviction Period: %.1f years%n", config.getConvictionPeriodYears()));
        sb.append(String.format("Stock Type: %s (Target: %.1f%%)%n",
                config.getStockType(), config.getStockType().getTargetReturnPercentage() * 100));
        sb.append(String.format("Deployment Style: %s%n", config.getDeploymentStyle()));
        sb.append(String.format("Initial Price: $%.2f%n", config.getInitialPrice()));
        sb.append(String.format("Scenario: %s%n", config.getScenario()));
        sb.append("\n");

        // Overall Results
        sb.append("--- Overall Results ---\n");
        sb.append(String.format("Days Simulated: %d%n", totalDaysSimulated));
        sb.append(String.format("Partitions: %d (Success: %d, Kill: %d, Neutral: %d)%n",
                partitionsCreated, successfulPartitions, killedPartitions, neutralPartitions));
        sb.append(String.format("Capital Invested: $%.2f of $%.2f (%.1f%%)%n",
                totalCapitalInvested, config.getTotalCapital(),
                (totalCapitalInvested / config.getTotalCapital()) * 100));
        sb.append(String.format("Shares Accumulated: %.4f%n", totalSharesAccumulated));
        sb.append(String.format("Average Price: $%.2f%n", averagePricePaid));
        sb.append(String.format("Final Price: $%.2f%n", finalMarketPrice));
        sb.append(String.format("Portfolio Value: $%.2f%n", finalPortfolioValue));
        sb.append(String.format("Overall Return: %.2f%%%n", overallReturnPct));
        sb.append("\n");

        // Partition Details
        if (partitionResults != null && !partitionResults.isEmpty()) {
            sb.append("--- Partition Details ---\n");
            for (PartitionResult pr : partitionResults) {
                sb.append(String.format("Partition %d: %s | Invested: $%.2f | Shares: %.4f | " +
                                "Avg: $%.2f | Days: %d | Growth: %d | Return: %.2f%%%n",
                        pr.getPartitionIndex(),
                        pr.getEndReason(),
                        pr.getCapitalInvested(),
                        pr.getSharesAccumulated(),
                        pr.getAvgPrice(),
                        pr.getDaysActive(),
                        pr.getSuccessfulGrowthDays(),
                        pr.getCumulativeReturnPct()));
            }
        }

        sb.append("\n============================================\n");
        return sb.toString();
    }
}
