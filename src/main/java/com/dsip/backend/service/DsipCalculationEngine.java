package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.mapper.DsipTrackerMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Core calculation engine for DSIP (Daily Smart Investment Plan).
 * Contains all mathematical formulas for:
 * - Daily investment recommendations
 * - Opportunity multiplier calculation
 * - Contingency multiplier calculation
 * - Abnormal dip detection
 * - Partition progress calculation
 * - Partition lifecycle evaluation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DsipCalculationEngine {

    private final DsipProperties dsipProperties;
    private final com.dsip.backend.util.FinancialCalculator financialCalculator;
    private final DsipTrackerMapper dsipTrackerMapper;



    // ========== MAIN CALCULATION METHODS ==========

    /**
     * Calculate the recommended daily investment amount.
     * Formula: dailyInvestment = neutralCapital × opportunityMultiplier × contingencyMultiplier
     *
     * @param context the calculation context containing all required data
     * @return the investment recommendation with breakdown
     */
    public InvestmentRecommendation calculateDailyInvestment(CalculationContext context) {
        double neutralCapital = calculateNeutralCapital(context.getPartition());
        OpportunityResult opportunityResult = calculateOpportunityMultiplier(context);
        double contingencyMultiplier = calculateContingencyMultiplier(context);

        double rawInvestment = neutralCapital * opportunityResult.getMultiplier() * contingencyMultiplier;

        // Cap by remaining partition capital
        double remainingPartitionCapital = context.getPartition().getPartitionCapitalAllocated()
                - context.getPartition().getCapitalInvestedSoFar();
        double recommendedAmount = Math.min(rawInvestment, Math.max(0, remainingPartitionCapital));

        return InvestmentRecommendation.builder()
                .recommendedAmount(round(recommendedAmount, 2))
                .neutralCapital(round(neutralCapital, 2))
                .opportunityMultiplier(round(opportunityResult.getMultiplier(), 4))
                .contingencyMultiplier(round(contingencyMultiplier, 4))
                .finalMultiplier(round(opportunityResult.getMultiplier() * contingencyMultiplier, 4))
                .signals(opportunityResult.getSignals())
                .isAbnormalDip(opportunityResult.isAbnormalDip())
                .build();
    }


    public InvestmentRecommendation calculateDailyInvestmentForSimulation(CalculationContext context) {
        double neutralCapital = calculateNeutralCapitalForSimulation(context);
        OpportunityResult opportunityResult = calculateOpportunityMultiplier(context);
        double contingencyMultiplier = calculateContingencyMultiplier(context);

        double rawInvestment = neutralCapital * opportunityResult.getMultiplier() * contingencyMultiplier;

        // Cap by remaining partition capital
        double remainingPartitionCapital = context.getPartition().getPartitionCapitalAllocated()
                - context.getPartition().getCapitalInvestedSoFar();
        double recommendedAmount = Math.min(rawInvestment, Math.max(0, remainingPartitionCapital));

        return InvestmentRecommendation.builder()
                .recommendedAmount(round(recommendedAmount, 2))
                .neutralCapital(round(neutralCapital, 2))
                .opportunityMultiplier(round(opportunityResult.getMultiplier(), 4))
                .contingencyMultiplier(round(contingencyMultiplier, 4))
                .finalMultiplier(round(opportunityResult.getMultiplier() * contingencyMultiplier, 4))
                .signals(opportunityResult.getSignals())
                .isAbnormalDip(opportunityResult.isAbnormalDip())
                .build();
    }

    /**
     * Calculate the neutral (baseline) daily capital.
     * Formula: neutralCapital = partitionAllocatedCapital / partitionCycleLength
     *
     * @param partition the current partition
     * @return neutral capital amount
     */
    public double calculateNeutralCapital(DsipPartition partition) {
        if (partition.getPartitionCapitalAllocated() == null ||
            partition.getExpectedPartitionDays() == null ||
            partition.getExpectedPartitionDays() == 0) {
            return 0.0;
        }
        double daysElapsed = financialCalculator.calculateDaysBetween(partition.getCreatedAt(), Instant.now());
        // 🚨 SAFETY: Zombie / stale partition detection
        if (daysElapsed >= partition.getExpectedPartitionDays() * 2) {
            throw new IllegalStateException(
                    "Partition has been inactive for too long. " +
                            "Days elapsed: " + daysElapsed +
                            ", Expected days: " + partition.getExpectedPartitionDays()
            );
        }
        double remainingDays = partition.getExpectedPartitionDays() - daysElapsed ;
        if (remainingDays <= 0) {
            remainingDays = 1; // avoid division by zero
        }
        double capitalRemaining = partition.getPartitionCapitalAllocated() - partition.getCapitalInvestedSoFar();

        double minimumTradableAmount = Math.max(1,
                capitalRemaining / remainingDays );
        return minimumTradableAmount ;
    }


    public double calculateNeutralCapitalForSimulation(CalculationContext context) {
        DsipPartition partition = context.getPartition() ;
        DsipTracker tracker = context.getTracker() ;
        if (partition.getPartitionCapitalAllocated() == null ||
                partition.getExpectedPartitionDays() == null ||
                partition.getExpectedPartitionDays() == 0) {
            return 0.0;
        }
        Instant referencedDate = null ;
        List<com.dsip.backend.entity.DsipExecution> latestExecutions = dsipTrackerMapper
                .findExecutionsByTrackerId(tracker.getTrackerId(), 1);

        if(latestExecutions!=null && !latestExecutions.isEmpty()) {
            referencedDate = latestExecutions.get(0).getCreatedAt() ;
        }
        else {
            referencedDate = partition.getCreatedAt() ;
        }

        double daysElapsed = financialCalculator.calculateDaysBetween(partition.getCreatedAt(), referencedDate);

        double capitalRemaining = partition.getPartitionCapitalAllocated() - partition.getCapitalInvestedSoFar();
        double minimumTradableAmount = Math.max(1,
                capitalRemaining / (partition.getExpectedPartitionDays() - daysElapsed));
        return minimumTradableAmount ;
    }

    // ========== OPPORTUNITY MULTIPLIER ==========

    /**
     * Calculate the opportunity multiplier based on price deviation and lock-in signals.
     *
     * @param context the calculation context
     * @return opportunity result with multiplier and signal breakdown
     */
    public OpportunityResult calculateOpportunityMultiplier(CalculationContext context) {
        // Calculate average price deviation signal
        double avgHoldingPrice = calculateAverageHoldingPrice(context.getPartition(), context.getExecutionPrice());
        double avgDeviationPct = calculateAverageDeviation(avgHoldingPrice, context.getExecutionPrice());
        double avgSignal = clamp(
                avgDeviationPct / dsipProperties.getAvgDeviationNormalizationFactor(),
                -1.0, 1.0
        );

        // Calculate lock-in deviation signal
        // lockInPct is already in percentage form (e.g., -5.0 for 5% below prev close)
        // Negative lock-in (red day) → negative signal
        double lockInSignal = clamp(
                context.getLockInPct() / (dsipProperties.getLockInNormalizationFactor() * 100),
                -1.0, 1.0
        );

        // Weighted opportunity signal
        // NOTE: lockInSignal is NEGATED because:
        // - Negative lock-in (red day, price dropped) = buying opportunity = should ADD to signal
        // - Positive lock-in (green day, price rose) = less opportunity = should SUBTRACT from signal
        // The spec interpretation says: "-8% → signal = -1 (inverted to +1 for buy)"
        double rawOpportunitySignal = (dsipProperties.getAvgDeviationWeight() * avgSignal)
                - (dsipProperties.getLockInWeight() * lockInSignal);

        // Conviction amplification (adapted for 0-100 scale)
        double convictionAmplifier = calculateConvictionAmplifier(context.getConvictionScore());

        // Calculate base opportunity multiplier
        double opportunityMultiplier = 1.0 + (rawOpportunitySignal * convictionAmplifier);

        // Detect abnormal dip
        boolean isAbnormalDip = detectAbnormalDip(context.getPartition(), avgDeviationPct);

        // Apply appropriate cap based on abnormal dip
        double maxCap = isAbnormalDip
                ? dsipProperties.getOpportunityMultiplierMaxAbnormalDip()
                : dsipProperties.getOpportunityMultiplierMaxNormal();

        opportunityMultiplier = clamp(
                opportunityMultiplier,
                dsipProperties.getOpportunityMultiplierMin(),
                maxCap
        );

        OpportunitySignals signals = OpportunitySignals.builder()
                .avgHoldingPrice(round(avgHoldingPrice, 4))
                .avgDeviationPct(round(avgDeviationPct * 100, 2)) // Convert to percentage
                .avgSignal(round(avgSignal, 4))
                .lockInPct(context.getLockInPct())
                .lockInSignal(round(lockInSignal, 4))
                .rawOpportunitySignal(round(rawOpportunitySignal, 4))
                .convictionAmplifier(round(convictionAmplifier, 4))
                .build();

        return OpportunityResult.builder()
                .multiplier(opportunityMultiplier)
                .signals(signals)
                .isAbnormalDip(isAbnormalDip)
                .build();
    }

    /**
     * Calculate average holding price for the partition.
     * If no holdings exist, returns the current execution price.
     *
     * @param partition the current partition
     * @param currentExecutionPrice the current execution price
     * @return average holding price
     */
    public double calculateAverageHoldingPrice(DsipPartition partition, double currentExecutionPrice) {
        if (partition.getCapitalInvestedSoFar() == null ||
            partition.getCapitalInvestedSoFar() == 0 ||
            partition.getNoOfSharesBought() == null ||
            partition.getNoOfSharesBought() == 0) {
            // First execution - use current price as average
            return currentExecutionPrice;
        }
        return partition.getCapitalInvestedSoFar() / partition.getNoOfSharesBought();
    }

    /**
     * Calculate average price deviation.
     * Positive deviation means current price is below average (buy signal).
     *
     * @param avgHoldingPrice average holding price
     * @param executionPrice current execution price
     * @return deviation as decimal (e.g., 0.08 for 8%)
     */
    public double calculateAverageDeviation(double avgHoldingPrice, double executionPrice) {
        if (avgHoldingPrice == 0) {
            return 0.0;
        }
        return (avgHoldingPrice - executionPrice) / avgHoldingPrice;
    }

    /**
     * Calculate conviction amplifier from conviction score (0-100 scale).
     * Formula adapted from spec: 0.6 + (convictionScore / 100) * 0.6
     * Range: 0.6 (score=0) to 1.2 (score=100)
     *
     * @param convictionScore score from 0-100
     * @return conviction amplifier
     */
    public double calculateConvictionAmplifier(int convictionScore) {
        int clampedScore = (int) clamp(convictionScore, 0, 100);
        return 0.6 + (clampedScore / 100.0) * 0.6;
    }

    // ========== ABNORMAL DIP DETECTION ==========

    /**
     * Detect if current price deviation represents an abnormal dip.
     * Conditions:
     * 1. Today's dip is worse than max negative deviation ever seen, OR
     * 2. Today's dip is >= 2x average negative deviation AND >= 70% of max negative deviation
     *
     * @param partition the current partition with deviation history
     * @param currentDeviationPct current price deviation as decimal
     * @return true if abnormal dip detected
     */
    public boolean detectAbnormalDip(DsipPartition partition, double currentDeviationPct) {
        // Only negative deviations can be dips
        if (currentDeviationPct >= 0) {
            return false;
        }

        double todayDip = currentDeviationPct; // Already negative

        Double avgNegDev = partition.getAvgNegativeDeviation();
        Double maxNegDev = partition.getMaxNegativeDeviation();

        // No history yet - can't determine abnormal
        if (avgNegDev == null || maxNegDev == null || partition.getNegativeDeviationCount() == null
                || partition.getNegativeDeviationCount() == 0) {
            return false;
        }

        // Condition 1: Worse than max ever seen
        if (todayDip < maxNegDev) {
            log.debug("Abnormal dip detected: todayDip ({}) < maxNegDev ({})", todayDip, maxNegDev);
            return true;
        }

        // Condition 2: Significantly worse than average AND approaching max
        boolean worseThanTwiceAvg = todayDip <= (2 * avgNegDev);
        boolean approaching70PctMax = todayDip <= (0.7 * maxNegDev);

        if (worseThanTwiceAvg && approaching70PctMax) {
            log.debug("Abnormal dip detected: todayDip ({}) <= 2*avgNegDev ({}) AND <= 0.7*maxNegDev ({})",
                    todayDip, 2 * avgNegDev, 0.7 * maxNegDev);
            return true;
        }

        return false;
    }

    /**
     * Update partition's negative deviation statistics.
     *
     * @param partition the partition to update
     * @param deviationPct current deviation as decimal
     */
    public void updateNegativeDeviationStats(DsipPartition partition, double deviationPct) {
        if (deviationPct >= 0) {
            return; // Only track negative deviations
        }

        Integer count = partition.getNegativeDeviationCount();
        if (count == null) {
            count = 0;
        }

        Double avgNegDev = partition.getAvgNegativeDeviation();
        if (avgNegDev == null) {
            avgNegDev = 0.0;
        }

        Double maxNegDev = partition.getMaxNegativeDeviation();
        if (maxNegDev == null) {
            maxNegDev = 0.0;
        }

        // Update running average
        double newAvg = ((avgNegDev * count) + deviationPct) / (count + 1);
        partition.setAvgNegativeDeviation(newAvg);

        // Update max (more negative is worse)
        if (deviationPct < maxNegDev) {
            partition.setMaxNegativeDeviation(deviationPct);
        }

        partition.setNegativeDeviationCount(count + 1);
    }

    // ========== CONTINGENCY MULTIPLIER ==========

    /**
     * Calculate contingency multiplier based on partition progress.
     * Linear decay from max (1.1) to min (0.7) as progress increases.
     * Formula: contingencyMultiplier = 1.1 - (partitionProgress * 0.4)
     *
     * @param context the calculation context
     * @return contingency multiplier
     */
    public double calculateContingencyMultiplier(CalculationContext context) {
        double partitionProgress = calculatePartitionProgress(context);

        double multiplier = dsipProperties.getContingencyMultiplierMax()
                - (partitionProgress * dsipProperties.getContingencyDecayFactor());

        return clamp(
                multiplier,
                dsipProperties.getContingencyMultiplierMin(),
                dsipProperties.getContingencyMultiplierMax()
        );
    }

    // ========== PARTITION PROGRESS ==========

    /**
     * Calculate partition progress as weighted combination of return and growth progress.
     * Formula: progress = (0.8 × returnProgress) + (0.2 × growthProgress)
     *
     * @param context the calculation context
     * @return partition progress (0.0 to 1.0+)
     */
    public double calculatePartitionProgress(CalculationContext context) {
        double returnProgress = calculateReturnProgress(context);
        double growthProgress = calculateGrowthProgress(context.getPartition());

        return (dsipProperties.getReturnWeight() * returnProgress)
                + (dsipProperties.getGrowthWeight() * growthProgress);
    }

    /**
     * Calculate return progress relative to target return.
     * Formula: returnProgress = cumulativeReturnPct / targetReturnPct
     *
     * @param context the calculation context
     * @return return progress (can exceed 1.0 if return exceeds target)
     */
    public double calculateReturnProgress(CalculationContext context) {
        double cumulativeReturnPct = calculateCumulativeReturnPct(
                context.getPartition(),
                context.getCurrentMarketPrice()
        );

        StockType stockType = context.getStockType() != null
                ? context.getStockType()
                : StockType.MIDCAP; // Default

        double targetReturnPct = dsipProperties.getTargetReturnPercentageForStockType(stockType);

        if (targetReturnPct == 0) {
            return 0.0;
        }

        return cumulativeReturnPct / targetReturnPct;
    }

    /**
     * Calculate growth progress (growth persistence).
     * Formula: growthProgress = successfulGrowthDays / partitionCycleLength
     *
     * @param partition the current partition
     * @return growth progress (0.0 to 1.0)
     */
    public double calculateGrowthProgress(DsipPartition partition) {
        if (partition.getExpectedPartitionDays() == null || partition.getExpectedPartitionDays() == 0) {
            return 0.0;
        }

        int growthCount = partition.getSuccessfulGrowthCount() != null
                ? partition.getSuccessfulGrowthCount()
                : 0;

        return (double) growthCount / partition.getExpectedPartitionDays();
    }

    /**
     * Calculate cumulative return percentage for the partition.
     * Formula: ((currentPrice - costPerShare) / costPerShare) × 100
     *
     * @param partition the current partition
     * @param currentMarketPrice current market price
     * @return cumulative return as percentage (e.g., 15.5 for 15.5%)
     */
    public double calculateCumulativeReturnPct(DsipPartition partition, double currentMarketPrice) {
        if (partition.getCapitalInvestedSoFar() == null ||
            partition.getCapitalInvestedSoFar() == 0 ||
            partition.getNoOfSharesBought() == null ||
            partition.getNoOfSharesBought() == 0) {
            return 0.0;
        }

        double costPerShare = partition.getCapitalInvestedSoFar() / partition.getNoOfSharesBought();

        if (costPerShare == 0) {
            return 0.0;
        }

        return ((currentMarketPrice - costPerShare) / costPerShare) * 100;
    }

    // ========== PARTITION LIFECYCLE ==========

    /**
     * Evaluate partition lifecycle to determine if it should end and with what status.
     *
     * @param context the calculation context
     * @return lifecycle result with status and reason
     */
    public LifecycleResult evaluateLifecycle(CalculationContext context) {
        DsipPartition partition = context.getPartition();

        double timeProgress = calculateTimeProgress(partition);
        double capitalProgress = calculateCapitalProgress(partition);
        double partitionProgress = calculatePartitionProgress(context);
        double cumulativeReturnPct = calculateCumulativeReturnPct(partition, context.getCurrentMarketPrice());

        // 1. SUCCESS - Target achieved
        boolean progressMet = partitionProgress >= dsipProperties.getSuccessProgressThreshold();
        boolean timeMet = timeProgress >= dsipProperties.getSuccessTimeThreshold();
        boolean capitalMet = capitalProgress >= dsipProperties.getSuccessCapitalThreshold();

        if (progressMet && (timeMet || capitalMet)) {
            return LifecycleResult.builder()
                    .status(PartitionStatus.COMPLETED)
                    .shouldEnd(true)
                    .reason("SUCCESS: Partition progress >= 80% with time >= 100% or capital >= 90%")
                    .metrics(buildMetrics(timeProgress, capitalProgress, partitionProgress, cumulativeReturnPct))
                    .build();
        }

        // 2. KILL - Severe loss (hard -30% stop)
        if (cumulativeReturnPct <= dsipProperties.getKillReturnThreshold()) {
            return LifecycleResult.builder()
                    .status(PartitionStatus.KILL_SWITCH_POOR_GROWTH)
                    .shouldEnd(true)
                    .reason(String.format("KILL: Cumulative return (%.2f%%) <= %.0f%%",
                            cumulativeReturnPct, dsipProperties.getKillReturnThreshold()))
                    .metrics(buildMetrics(timeProgress, capitalProgress, partitionProgress, cumulativeReturnPct))
                    .build();
        }

        // 3. KILL - Time exhaustion with negative return
        if (cumulativeReturnPct < 0 && timeProgress >= dsipProperties.getKillTimeThreshold()) {
            return LifecycleResult.builder()
                    .status(PartitionStatus.KILL_SWITCH_STAGNATION)
                    .shouldEnd(true)
                    .reason(String.format("KILL: Negative return (%.2f%%) with time progress >= %.0f%%",
                            cumulativeReturnPct, dsipProperties.getKillTimeThreshold() * 100))
                    .metrics(buildMetrics(timeProgress, capitalProgress, partitionProgress, cumulativeReturnPct))
                    .build();
        }

        // 4. NEUTRAL - Time exhaustion but non-negative return
        if (cumulativeReturnPct >= 0 && timeProgress >= dsipProperties.getNeutralTimeThreshold()) {
            return LifecycleResult.builder()
                    .status(PartitionStatus.NEUTRAL)
                    .shouldEnd(true)
                    .reason(String.format("NEUTRAL: Non-negative return (%.2f%%) with time progress >= %.0f%%",
                            cumulativeReturnPct, dsipProperties.getNeutralTimeThreshold() * 100))
                    .metrics(buildMetrics(timeProgress, capitalProgress, partitionProgress, cumulativeReturnPct))
                    .build();
        }

        // 5. CONTINUE - Still active
        return LifecycleResult.builder()
                .status(PartitionStatus.ACTIVE)
                .shouldEnd(false)
                .reason("CONTINUE: No end conditions met")
                .metrics(buildMetrics(timeProgress, capitalProgress, partitionProgress, cumulativeReturnPct))
                .build();
    }

    /**
     * Calculate time progress for the partition.
     * Formula: daysElapsed / expectedPartitionDays
     *
     * @param partition the current partition
     * @return time progress as ratio (e.g., 1.5 for 150%)
     */
    public double calculateTimeProgress(DsipPartition partition) {
        if (partition.getExpectedPartitionDays() == null || partition.getExpectedPartitionDays() == 0) {
            return 0.0;
        }

        int daysElapsed = calculateTradingDaysElapsed(partition.getCreatedAt());
        return (double) daysElapsed / partition.getExpectedPartitionDays();
    }

    /**
     * Calculate capital deployment progress for the partition.
     * Formula: capitalInvestedSoFar / partitionCapitalAllocated
     *
     * @param partition the current partition
     * @return capital progress as ratio (e.g., 0.85 for 85%)
     */
    public double calculateCapitalProgress(DsipPartition partition) {
        if (partition.getPartitionCapitalAllocated() == null ||
            partition.getPartitionCapitalAllocated() == 0) {
            return 0.0;
        }

        double invested = partition.getCapitalInvestedSoFar() != null
                ? partition.getCapitalInvestedSoFar()
                : 0.0;

        return invested / partition.getPartitionCapitalAllocated();
    }

    // ========== PARTITION ALLOCATION ==========

    /**
     * Calculate capital allocation for a new partition.
     * Rebases on remaining capital each time.
     *
     * @param tracker the tracker
     * @param partitionIndex the index of the new partition (1-based)
     * @return allocated capital for this partition
     */
    public double calculatePartitionAllocation(DsipTracker tracker, int partitionIndex) {
        int convictionDays = (int) (tracker.getConvictionPeriodYears() * dsipProperties.getTradingDaysPerYear());
        int partitionCycleLength = tracker.getPartitionDays();

        if (partitionCycleLength == 0) {
            return 0.0;
        }

        int expectedPartitions = convictionDays / partitionCycleLength;
        if (expectedPartitions == 0) {
            expectedPartitions = 1;
        }

        int partitionsPerPhase = Math.max(1, expectedPartitions / dsipProperties.getPhaseCount());

        // Determine current phase (0-indexed)
        int phaseIndex = (partitionIndex - 1) / partitionsPerPhase;
        phaseIndex = Math.min(phaseIndex, dsipProperties.getPhaseCount() - 1);

        // Get phase weight from load factor
        DeploymentStyle style = DeploymentStyle.fromValue(tracker.getDeploymentStyle());
        List<Double> phaseWeights = dsipProperties.getLoadFactor().getByDeploymentStyle(style);
        double phaseWeight = phaseWeights.get(phaseIndex);

        // Calculate remaining partitions in this phase
        int partitionIndexInPhase = (partitionIndex - 1) % partitionsPerPhase;
        int remainingInPhase = partitionsPerPhase - partitionIndexInPhase;

        // Calculate remaining capital
        double totalDeployed = tracker.getTotalCapitalInvestedSoFar() != null
                ? tracker.getTotalCapitalInvestedSoFar()
                : 0.0;
        double remainingCapital = tracker.getTotalCapitalPlanned() - totalDeployed;

        if (remainingCapital <= 0 || remainingInPhase <= 0) {
            return 0.0;
        }

        return (remainingCapital * phaseWeight) / remainingInPhase;
    }

    // ========== GROWTH DAY EVALUATION ==========

    /**
     * Check if today qualifies as a successful growth day.
     * Condition: todayPrice > yesterdayPrice AND cumulativeReturn > 0
     *
     * @param todayPrice today's market price
     * @param yesterdayPrice yesterday's market price
     * @param cumulativeReturnPct current cumulative return percentage
     * @return true if this is a successful growth day
     */
    public boolean isSuccessfulGrowthDay(double todayPrice, double yesterdayPrice, double cumulativeReturnPct) {
        return todayPrice > yesterdayPrice && cumulativeReturnPct > 0;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Clamp a value between min and max.
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Round a value to specified decimal places.
     */
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException("Places must be non-negative");
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    /**
     * Calculate trading days elapsed since a given instant.
     * Approximation: calendar days × (252/365)
     */
    public int calculateTradingDaysElapsed(Instant startDate) {
        if (startDate == null) {
            return 0;
        }

        LocalDate start = startDate.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate now = LocalDate.now();
        long calendarDays = ChronoUnit.DAYS.between(start, now);

        return (int) (calendarDays * dsipProperties.getTradingDaysPerYear() / 365.0);
    }

    /**
     * Check if tracker is within its conviction period.
     */
    public boolean isWithinConvictionPeriod(DsipTracker tracker) {
        if (tracker.getCreatedAt() == null || tracker.getConvictionPeriodYears() == null) {
            return false;
        }

        int convictionDays = (int) (tracker.getConvictionPeriodYears() * dsipProperties.getTradingDaysPerYear());
        int daysElapsed = calculateTradingDaysElapsed(tracker.getCreatedAt());

        return daysElapsed < convictionDays;
    }

    private LifecycleMetrics buildMetrics(double timeProgress, double capitalProgress,
                                          double partitionProgress, double cumulativeReturnPct) {
        return LifecycleMetrics.builder()
                .timeProgressPct(round(timeProgress * 100, 2))
                .capitalProgressPct(round(capitalProgress * 100, 2))
                .partitionProgressPct(round(partitionProgress * 100, 2))
                .cumulativeReturnPct(round(cumulativeReturnPct, 2))
                .build();
    }

    // ========== DTOs ==========

    /**
     * Input context for calculations.
     */
    @Data
    @Builder
    public static class CalculationContext {
        private DsipTracker tracker;
        private DsipPartition partition;
        private double executionPrice;
        private double currentMarketPrice;
        private double lockInPct; // Lock-in percentage (e.g., -5.0 for 5% below prev close)
        private int convictionScore; // 0-100 scale
        private StockType stockType;
        private Double yesterdayPrice; // For growth day calculation
    }

    /**
     * Investment recommendation result.
     */
    @Data
    @Builder
    public static class InvestmentRecommendation {
        private double recommendedAmount;
        private double neutralCapital;
        private double opportunityMultiplier;
        private double contingencyMultiplier;
        private double finalMultiplier;
        private OpportunitySignals signals;
        private boolean isAbnormalDip;
    }

    /**
     * Opportunity multiplier calculation result.
     */
    @Data
    @Builder
    public static class OpportunityResult {
        private double multiplier;
        private OpportunitySignals signals;
        private boolean isAbnormalDip;
    }

    /**
     * Breakdown of signals used in opportunity calculation.
     */
    @Data
    @Builder
    public static class OpportunitySignals {
        private double avgHoldingPrice;
        private double avgDeviationPct; // As percentage
        private double avgSignal; // -1 to +1
        private double lockInPct; // As percentage
        private double lockInSignal; // -1 to +1
        private double rawOpportunitySignal;
        private double convictionAmplifier;
    }

    /**
     * Lifecycle evaluation result.
     */
    @Data
    @Builder
    public static class LifecycleResult {
        private PartitionStatus status;
        private boolean shouldEnd;
        private String reason;
        private LifecycleMetrics metrics;
    }

    /**
     * Metrics used in lifecycle evaluation.
     */
    @Data
    @Builder
    public static class LifecycleMetrics {
        private double timeProgressPct;
        private double capitalProgressPct;
        private double partitionProgressPct;
        private double cumulativeReturnPct;
    }
}
