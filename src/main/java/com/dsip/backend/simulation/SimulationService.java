package com.dsip.backend.simulation;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.model.PartitionEndDecision;
import com.dsip.backend.model.PartitionPlan;
import com.dsip.backend.service.DsipCalculationEngine;
import com.dsip.backend.service.DsipCalculationEngine.CalculationContext;
import com.dsip.backend.service.DsipCalculationEngine.InvestmentRecommendation;
import com.dsip.backend.service.DsipCalculationEngine.OpportunitySignals;
import com.dsip.backend.service.PartitionAllocationPolicy;
import com.dsip.backend.service.PartitionLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Service for running DSIP simulations to validate calculation logic.
 * Supports both generated price scenarios and historical OHLC data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final DsipCalculationEngine calculationEngine;
    private final PartitionAllocationPolicy allocationPolicy;
    private final PartitionLifecyclePolicy lifecyclePolicy;
    private final DsipProperties dsipProperties;
    private final CsvPriceDataLoader csvLoader;

    /**
     * Run a complete DSIP simulation.
     *
     * @param config simulation configuration
     * @return simulation results
     */
    public SimulationResult runSimulation(SimulationConfig config) {
        log.info("Starting DSIP simulation - mode: {}", config.isHistoricalMode() ? "HISTORICAL" : "GENERATED");

        // Load historical data if needed
        List<OhlcData> historicalData = loadHistoricalData(config);

        if (config.isHistoricalMode() && (historicalData == null || historicalData.isEmpty())) {
            throw new IllegalArgumentException("Historical mode requires OHLC data but none was provided");
        }

        if (config.isHistoricalMode()) {
            return runHistoricalSimulation(config, historicalData);
        } else {
            return runGeneratedSimulation(config);
        }
    }

    /**
     * Run simulation with historical OHLC data.
     */
    private SimulationResult runHistoricalSimulation(SimulationConfig config, List<OhlcData> ohlcData) {
        log.info("Running historical simulation with {} days of data from {} to {}",
                ohlcData.size(),
                ohlcData.get(0).getDate(),
                ohlcData.get(ohlcData.size() - 1).getDate());

        // Initialize tracker
        DsipTracker tracker = createSimulatedTracker(config, ohlcData.get(0).getClose());

        // Results tracking
        List<SimulationResult.PartitionResult> partitionResults = new ArrayList<>();
        List<SimulationResult.DayResult> dayResults = new ArrayList<>();
        int successCount = 0;
        int killCount = 0;
        int neutralCount = 0;

        // Create first partition
        DsipPartition currentPartition = createFirstPartition(tracker, config);
        int partitionStartDay = 0;

        // Track current prices
        double currentPrice = ohlcData.get(0).getClose();
        double previousClose = currentPrice;

        boolean simulationActive = true;
        int dayIndex = 0;

        while (simulationActive && dayIndex < ohlcData.size()) {
            OhlcData today = ohlcData.get(dayIndex);
            dayIndex++;

            // Get prices based on configuration
            double executionPrice = getExecutionPrice(today, config.getExecutionPriceType());
            double lockInReference = getLockInReference(today, previousClose, config.getLockInReferenceType());
            double lockInPct = ((executionPrice - lockInReference) / lockInReference) * 100;
            double priceChangePct = today.calculateChangePercent(previousClose);

            currentPrice = today.getClose();

            // Build calculation context
            CalculationContext context = CalculationContext.builder()
                    .tracker(tracker)
                    .partition(currentPartition)
                    .executionPrice(executionPrice)
                    .currentMarketPrice(currentPrice)
                    .lockInPct(lockInPct)
                    .convictionScore(config.getBaseConvictionScore())
                    .stockType(config.getStockType())
                    .build();

            // Get recommendation with all signal details
            InvestmentRecommendation recommendation = calculationEngine.calculateDailyInvestment(context);
            double recommendedAmount = recommendation.getRecommendedAmount();

            // Execute trade
            double remainingCapital = currentPartition.getPartitionCapitalAllocated() - currentPartition.getCapitalInvestedSoFar();
            double executedAmount = Math.min(recommendedAmount, remainingCapital);
            executedAmount = Math.max(0, executedAmount);

            double sharesAcquired = executedAmount > 0 ? executedAmount / executionPrice : 0;

            // Update partition
            if (executedAmount > 0) {
                currentPartition.setCapitalInvestedSoFar(
                        currentPartition.getCapitalInvestedSoFar() + executedAmount);
                currentPartition.setNoOfSharesBought(
                        currentPartition.getNoOfSharesBought() + sharesAcquired);

                // Update tracker
                tracker.setTotalCapitalInvestedSoFar(
                        tracker.getTotalCapitalInvestedSoFar() + executedAmount);
                tracker.setSharesHeldSoFar(
                        tracker.getSharesHeldSoFar() + sharesAcquired);
            }

            // Check growth day (using close prices)
            double cumulativeReturnPct = calculationEngine.calculateCumulativeReturnPct(
                    currentPartition, currentPrice);
            boolean isGrowthDay = calculationEngine.isSuccessfulGrowthDay(
                    currentPrice, previousClose, cumulativeReturnPct);

            if (isGrowthDay) {
                currentPartition.setSuccessfulGrowthCount(
                        currentPartition.getSuccessfulGrowthCount() + 1);
            }

            // Update negative deviation stats
            if (executedAmount > 0) {
                double avgHoldingPrice = calculationEngine.calculateAverageHoldingPrice(
                        currentPartition, executionPrice);
                double avgDeviationPct = calculationEngine.calculateAverageDeviation(
                        avgHoldingPrice, executionPrice);
                calculationEngine.updateNegativeDeviationStats(currentPartition, avgDeviationPct);
            }

            // Calculate progress metrics
            double timeProgress = calculationEngine.calculateTimeProgress(currentPartition);
            double capitalProgress = calculationEngine.calculateCapitalProgress(currentPartition);
            double partitionProgress = calculationEngine.calculatePartitionProgress(context);
            double returnProgress = calculationEngine.calculateReturnProgress(context);
            double growthProgress = calculationEngine.calculateGrowthProgress(currentPartition);

            // Extract signals from recommendation
            OpportunitySignals signals = recommendation.getSignals();

            // Record comprehensive day result
            SimulationResult.DayResult dayResult = SimulationResult.DayResult.builder()
                    // Basic Info
                    .dayNumber(dayIndex)
                    .partitionIndex(currentPartition.getPartitionIndex())
                    .date(today.getDate())
                    // Price Data
                    .openPrice(today.getOpen())
                    .highPrice(today.getHigh())
                    .lowPrice(today.getLow())
                    .closePrice(today.getClose())
                    .executionPrice(executionPrice)
                    .lockInReferencePrice(lockInReference)
                    // Price Changes
                    .priceChangePct(priceChangePct)
                    .lockInPct(lockInPct)
                    // Opportunity Multiplier Breakdown
                    .avgHoldingPrice(signals != null ? signals.getAvgHoldingPrice() : 0)
                    .avgDeviationPct(signals != null ? signals.getAvgDeviationPct() : 0)
                    .avgSignal(signals != null ? signals.getAvgSignal() : 0)
                    .lockInSignal(signals != null ? signals.getLockInSignal() : 0)
                    .rawOpportunitySignal(signals != null ? signals.getRawOpportunitySignal() : 0)
                    .convictionAmplifier(signals != null ? signals.getConvictionAmplifier() : 0)
                    .isAbnormalDip(recommendation.isAbnormalDip())
                    .opportunityMultiplier(recommendation.getOpportunityMultiplier())
                    // Contingency Multiplier Breakdown
                    .returnProgress(returnProgress)
                    .growthProgress(growthProgress)
                    .partitionProgress(partitionProgress)
                    .contingencyMultiplier(recommendation.getContingencyMultiplier())
                    // Investment Calculation
                    .neutralCapital(recommendation.getNeutralCapital())
                    .finalMultiplier(recommendation.getFinalMultiplier())
                    .recommendedAmount(recommendedAmount)
                    .executedAmount(executedAmount)
                    .sharesAcquired(sharesAcquired)
                    // Partition Running Totals
                    .partitionCapitalInvested(currentPartition.getCapitalInvestedSoFar())
                    .partitionSharesBought(currentPartition.getNoOfSharesBought())
                    .partitionRemainingCapital(currentPartition.getPartitionCapitalAllocated() - currentPartition.getCapitalInvestedSoFar())
                    // Tracker Running Totals
                    .totalCapitalInvested(tracker.getTotalCapitalInvestedSoFar())
                    .totalSharesHeld(tracker.getSharesHeldSoFar())
                    .portfolioValue(tracker.getSharesHeldSoFar() * currentPrice)
                    // Performance
                    .cumulativeReturnPct(cumulativeReturnPct)
                    .isGrowthDay(isGrowthDay)
                    .growthDayCount(currentPartition.getSuccessfulGrowthCount())
                    // Progress
                    .timeProgressPct(timeProgress * 100)
                    .capitalProgressPct(capitalProgress * 100)
                    .partitionProgressPct(partitionProgress * 100)
                    .build();

            dayResults.add(dayResult);

            // Evaluate lifecycle
            PartitionEndDecision decision = lifecyclePolicy.evaluate(tracker, currentPartition, currentPrice);

            if (decision.isShouldEnd()) {
                // Mark the day result with partition end reason
                dayResult.setPartitionEndReason(decision.getReason().toString());

                // Record partition result
                int daysActive = dayIndex - partitionStartDay;

                SimulationResult.PartitionResult partitionResult = buildPartitionResult(
                        currentPartition, daysActive, cumulativeReturnPct, decision.getReason(),
                        partitionProgress * 100, timeProgress * 100, capitalProgress * 100);

                partitionResults.add(partitionResult);

                // Count partition outcomes
                switch (decision.getReason()) {
                    case SUCCESS -> successCount++;
                    case KILL_SWITCH_STAGNATION, KILL_SWITCH_POOR_GROWTH, KILL_SWITCH_ZOMBIE -> killCount++;
                    case NEUTRAL_PARTITION -> neutralCount++;
                    default -> {}
                }

                // Check if we should create next partition
                double trackerRemainingCapital = tracker.getTotalCapitalPlanned() - tracker.getTotalCapitalInvestedSoFar();

                if (trackerRemainingCapital > 0 && partitionResults.size() < config.getMaxPartitions()) {
                    currentPartition = createNextPartition(tracker, currentPartition, config, partitionResults);
                    partitionStartDay = dayIndex;
                    log.debug("Created partition {} after {} on day {}",
                            currentPartition.getPartitionIndex(), decision.getReason(), dayIndex);
                } else {
                    simulationActive = false;
                    log.info("Simulation ended after {} partitions, {} days", partitionResults.size(), dayIndex);
                }
            }

            // Update previous close for next iteration
            previousClose = today.getClose();
        }

        return buildSimulationResult(config, dayResults.size(), partitionResults,
                successCount, killCount, neutralCount, tracker, currentPrice, dayResults);
    }

    /**
     * Run simulation with generated price scenarios.
     */
    private SimulationResult runGeneratedSimulation(SimulationConfig config) {
        log.info("Running generated simulation with scenario: {}", config.getScenario());

        Random random = config.getRandomSeed() != null
                ? new Random(config.getRandomSeed())
                : new Random();

        // Initialize tracker
        DsipTracker tracker = createSimulatedTracker(config, config.getInitialPrice());
        double currentPrice = config.getInitialPrice();
        double previousPrice = currentPrice;

        // Results tracking
        List<SimulationResult.PartitionResult> partitionResults = new ArrayList<>();
        List<SimulationResult.DayResult> dayResults = new ArrayList<>();
        int totalDays = 0;
        int successCount = 0;
        int killCount = 0;
        int neutralCount = 0;

        // Create first partition
        DsipPartition currentPartition = createFirstPartition(tracker, config);
        int partitionStartDay = 0;

        // Price changes for custom scenario
        List<Double> priceChanges = generatePriceChanges(config, random);
        int priceChangeIndex = 0;

        // Simulate trading days
        int maxDays = (int) (config.getConvictionPeriodYears() * dsipProperties.getTradingDaysPerYear());
        boolean simulationActive = true;

        while (simulationActive && totalDays < maxDays) {
            totalDays++;

            // Get price change for today
            double priceChangePct;
            if (priceChangeIndex < priceChanges.size()) {
                priceChangePct = priceChanges.get(priceChangeIndex++);
            } else {
                priceChangePct = generateDailyPriceChange(config.getScenario(), random);
            }

            previousPrice = currentPrice;
            currentPrice = currentPrice * (1 + priceChangePct / 100);

            // Calculate lock-in percentage (how much below yesterday's close)
            double lockInPct = ((currentPrice - previousPrice) / previousPrice) * 100;
            double lockInReference = previousPrice;

            // Build calculation context
            CalculationContext context = CalculationContext.builder()
                    .tracker(tracker)
                    .partition(currentPartition)
                    .executionPrice(currentPrice)
                    .currentMarketPrice(currentPrice)
                    .lockInPct(lockInPct)
                    .convictionScore(config.getBaseConvictionScore())
                    .stockType(config.getStockType())
                    .build();

            // Get recommendation
            InvestmentRecommendation recommendation = calculationEngine.calculateDailyInvestment(context);
            double recommendedAmount = recommendation.getRecommendedAmount();

            // Execute trade
            double remainingCapital = currentPartition.getPartitionCapitalAllocated() - currentPartition.getCapitalInvestedSoFar();
            double executedAmount = Math.min(recommendedAmount, remainingCapital);
            executedAmount = Math.max(0, executedAmount);

            double executionPrice = currentPrice;
            double sharesAcquired = executedAmount > 0 ? executedAmount / executionPrice : 0;

            // Update partition
            if (executedAmount > 0) {
                currentPartition.setCapitalInvestedSoFar(
                        currentPartition.getCapitalInvestedSoFar() + executedAmount);
                currentPartition.setNoOfSharesBought(
                        currentPartition.getNoOfSharesBought() + sharesAcquired);

                // Update tracker
                tracker.setTotalCapitalInvestedSoFar(
                        tracker.getTotalCapitalInvestedSoFar() + executedAmount);
                tracker.setSharesHeldSoFar(
                        tracker.getSharesHeldSoFar() + sharesAcquired);
            }

            // Check growth day
            double cumulativeReturnPct = calculationEngine.calculateCumulativeReturnPct(
                    currentPartition, currentPrice);
            boolean isGrowthDay = calculationEngine.isSuccessfulGrowthDay(
                    currentPrice, previousPrice, cumulativeReturnPct);

            if (isGrowthDay) {
                currentPartition.setSuccessfulGrowthCount(
                        currentPartition.getSuccessfulGrowthCount() + 1);
            }

            // Update negative deviation stats
            if (executedAmount > 0) {
                double avgHoldingPrice = calculationEngine.calculateAverageHoldingPrice(
                        currentPartition, executionPrice);
                double avgDeviationPct = calculationEngine.calculateAverageDeviation(
                        avgHoldingPrice, executionPrice);
                calculationEngine.updateNegativeDeviationStats(currentPartition, avgDeviationPct);
            }

            // Calculate progress metrics
            double timeProgress = calculationEngine.calculateTimeProgress(currentPartition);
            double capitalProgress = calculationEngine.calculateCapitalProgress(currentPartition);
            double partitionProgress = calculationEngine.calculatePartitionProgress(context);
            double returnProgress = calculationEngine.calculateReturnProgress(context);
            double growthProgress = calculationEngine.calculateGrowthProgress(currentPartition);

            // Extract signals
            OpportunitySignals signals = recommendation.getSignals();

            // Record comprehensive day result
            SimulationResult.DayResult dayResult = SimulationResult.DayResult.builder()
                    // Basic Info
                    .dayNumber(totalDays)
                    .partitionIndex(currentPartition.getPartitionIndex())
                    // Price Data
                    .openPrice(previousPrice)
                    .closePrice(currentPrice)
                    .executionPrice(executionPrice)
                    .lockInReferencePrice(lockInReference)
                    // Price Changes
                    .priceChangePct(priceChangePct)
                    .lockInPct(lockInPct)
                    // Opportunity Multiplier Breakdown
                    .avgHoldingPrice(signals != null ? signals.getAvgHoldingPrice() : 0)
                    .avgDeviationPct(signals != null ? signals.getAvgDeviationPct() : 0)
                    .avgSignal(signals != null ? signals.getAvgSignal() : 0)
                    .lockInSignal(signals != null ? signals.getLockInSignal() : 0)
                    .rawOpportunitySignal(signals != null ? signals.getRawOpportunitySignal() : 0)
                    .convictionAmplifier(signals != null ? signals.getConvictionAmplifier() : 0)
                    .isAbnormalDip(recommendation.isAbnormalDip())
                    .opportunityMultiplier(recommendation.getOpportunityMultiplier())
                    // Contingency Multiplier Breakdown
                    .returnProgress(returnProgress)
                    .growthProgress(growthProgress)
                    .partitionProgress(partitionProgress)
                    .contingencyMultiplier(recommendation.getContingencyMultiplier())
                    // Investment Calculation
                    .neutralCapital(recommendation.getNeutralCapital())
                    .finalMultiplier(recommendation.getFinalMultiplier())
                    .recommendedAmount(recommendedAmount)
                    .executedAmount(executedAmount)
                    .sharesAcquired(sharesAcquired)
                    // Partition Running Totals
                    .partitionCapitalInvested(currentPartition.getCapitalInvestedSoFar())
                    .partitionSharesBought(currentPartition.getNoOfSharesBought())
                    .partitionRemainingCapital(currentPartition.getPartitionCapitalAllocated() - currentPartition.getCapitalInvestedSoFar())
                    // Tracker Running Totals
                    .totalCapitalInvested(tracker.getTotalCapitalInvestedSoFar())
                    .totalSharesHeld(tracker.getSharesHeldSoFar())
                    .portfolioValue(tracker.getSharesHeldSoFar() * currentPrice)
                    // Performance
                    .cumulativeReturnPct(cumulativeReturnPct)
                    .isGrowthDay(isGrowthDay)
                    .growthDayCount(currentPartition.getSuccessfulGrowthCount())
                    // Progress
                    .timeProgressPct(timeProgress * 100)
                    .capitalProgressPct(capitalProgress * 100)
                    .partitionProgressPct(partitionProgress * 100)
                    .build();

            dayResults.add(dayResult);

            // Evaluate lifecycle
            PartitionEndDecision decision = lifecyclePolicy.evaluate(tracker, currentPartition, currentPrice);

            if (decision.isShouldEnd()) {
                // Mark the day result with partition end reason
                dayResult.setPartitionEndReason(decision.getReason().toString());

                // Record partition result
                int daysActive = totalDays - partitionStartDay;

                SimulationResult.PartitionResult partitionResult = buildPartitionResult(
                        currentPartition, daysActive, cumulativeReturnPct, decision.getReason(),
                        partitionProgress * 100, timeProgress * 100, capitalProgress * 100);

                partitionResults.add(partitionResult);

                // Count partition outcomes
                switch (decision.getReason()) {
                    case SUCCESS -> successCount++;
                    case KILL_SWITCH_STAGNATION, KILL_SWITCH_POOR_GROWTH, KILL_SWITCH_ZOMBIE -> killCount++;
                    case NEUTRAL_PARTITION -> neutralCount++;
                    default -> {}
                }

                // Check if we should create next partition
                double trackerRemainingCapital = tracker.getTotalCapitalPlanned() - tracker.getTotalCapitalInvestedSoFar();

                if (trackerRemainingCapital > 0 && partitionResults.size() < config.getMaxPartitions()) {
                    currentPartition = createNextPartition(tracker, currentPartition, config, partitionResults);
                    partitionStartDay = totalDays;
                    log.debug("Created partition {} after {} on day {}",
                            currentPartition.getPartitionIndex(), decision.getReason(), totalDays);
                } else {
                    simulationActive = false;
                    log.info("Simulation ended after {} partitions, {} days", partitionResults.size(), totalDays);
                }
            }
        }

        return buildSimulationResult(config, totalDays, partitionResults,
                successCount, killCount, neutralCount, tracker, currentPrice, dayResults);
    }

    // ==================== Helper Methods ====================

    private List<OhlcData> loadHistoricalData(SimulationConfig config) {
        // Priority: provided data > CSV file path
        if (config.getHistoricalData() != null && !config.getHistoricalData().isEmpty()) {
            return config.getHistoricalData();
        }

        if (config.getCsvFilePath() != null && !config.getCsvFilePath().isEmpty()) {
            try {
                return csvLoader.loadFromFile(config.getCsvFilePath());
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to load CSV file: " + config.getCsvFilePath(), e);
            }
        }

        return null;
    }

    private double getExecutionPrice(OhlcData ohlc, SimulationConfig.ExecutionPriceType type) {
        return switch (type) {
            case OPEN -> ohlc.getOpen();
            case CLOSE -> ohlc.getClose();
            case VWAP -> (ohlc.getHigh() + ohlc.getLow() + ohlc.getClose()) / 3;
            case LOW -> ohlc.getLow();
            case HIGH -> ohlc.getHigh();
        };
    }

    private double getLockInReference(OhlcData today, double previousClose,
                                       SimulationConfig.LockInReferenceType type) {
        return switch (type) {
            case PREV_CLOSE -> previousClose;
            case OPEN -> today.getOpen();
        };
    }

    private DsipTracker createSimulatedTracker(SimulationConfig config, double initialPrice) {
        DeploymentStyle style = DeploymentStyle.fromKey(config.getDeploymentStyle());

        return DsipTracker.builder()
                .trackerId(0)
                .totalCapitalPlanned(config.getTotalCapital())
                .totalCapitalInvestedSoFar(0.0)
                .sharesHeldSoFar(0.0)
                .convictionPeriodYears(config.getConvictionPeriodYears())
                .baseConvictionScore(config.getBaseConvictionScore())
                .deploymentStyle(style.getValue())
                .partitionDays(config.getExpectedPartitionDays())
                .activePartitionIndex(1)
                .status(1) // ACTIVE
                .createdAt(Instant.now())
                .build();
    }

    private DsipPartition createFirstPartition(DsipTracker tracker, SimulationConfig config) {
        PartitionPlan plan = allocationPolicy.createPlan(tracker, 1, Collections.emptyList());

        return DsipPartition.builder()
                .partitionId(0)
                .trackerId(tracker.getTrackerId())
                .partitionIndex(1)
                .expectedPartitionDays(plan.getExpectedLengthDays())
                .partitionCapitalAllocated(plan.getAllocatedCapital())
                .capitalInvestedSoFar(0.0)
                .noOfSharesBought(0.0)
                .successfulGrowthCount(0)
                .avgNegativeDeviation(0.0)
                .negativeDeviationCount(0)
                .maxNegativeDeviation(0.0)
                .status(PartitionStatus.ACTIVE.getValue())
                .createdAt(Instant.now())
                .build();
    }

    private DsipPartition createNextPartition(DsipTracker tracker, DsipPartition previous,
                                               SimulationConfig config, List<SimulationResult.PartitionResult> results) {
        int nextIndex = previous.getPartitionIndex() + 1;

        Instant baseTime = Instant.now();
        List<DsipPartition> pastPartitions = results.stream()
                .filter(r -> r.getDaysActive() > 0)
                .map(r -> DsipPartition.builder()
                        .createdAt(baseTime)
                        .partitionEndDate(baseTime.plus(r.getDaysActive(), ChronoUnit.DAYS))
                        .build())
                .toList();

        PartitionPlan plan = allocationPolicy.createPlan(tracker, nextIndex, pastPartitions);

        return DsipPartition.builder()
                .partitionId(0)
                .trackerId(tracker.getTrackerId())
                .partitionIndex(nextIndex)
                .expectedPartitionDays(plan.getExpectedLengthDays())
                .partitionCapitalAllocated(plan.getAllocatedCapital())
                .capitalInvestedSoFar(0.0)
                .noOfSharesBought(0.0)
                .successfulGrowthCount(0)
                .avgNegativeDeviation(0.0)
                .negativeDeviationCount(0)
                .maxNegativeDeviation(0.0)
                .status(PartitionStatus.ACTIVE.getValue())
                .createdAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
    }

    private SimulationResult.PartitionResult buildPartitionResult(DsipPartition partition, int daysActive,
                                                                   double cumulativeReturnPct, EndReason reason,
                                                                   double partitionProgress, double timeProgress,
                                                                   double capitalProgress) {
        return SimulationResult.PartitionResult.builder()
                .partitionIndex(partition.getPartitionIndex())
                .capitalAllocated(partition.getPartitionCapitalAllocated())
                .capitalInvested(partition.getCapitalInvestedSoFar())
                .sharesAccumulated(partition.getNoOfSharesBought())
                .avgPrice(partition.getNoOfSharesBought() > 0
                        ? partition.getCapitalInvestedSoFar() / partition.getNoOfSharesBought()
                        : 0)
                .daysActive(daysActive)
                .successfulGrowthDays(partition.getSuccessfulGrowthCount())
                .cumulativeReturnPct(cumulativeReturnPct)
                .endReason(reason)
                .partitionProgressPct(partitionProgress)
                .timeProgressPct(timeProgress)
                .capitalProgressPct(capitalProgress)
                .build();
    }

    private SimulationResult buildSimulationResult(SimulationConfig config, int totalDays,
                                                    List<SimulationResult.PartitionResult> partitionResults,
                                                    int successCount, int killCount, int neutralCount,
                                                    DsipTracker tracker, double finalPrice,
                                                    List<SimulationResult.DayResult> dayResults) {
        double avgPrice = tracker.getSharesHeldSoFar() > 0
                ? tracker.getTotalCapitalInvestedSoFar() / tracker.getSharesHeldSoFar()
                : 0;
        double portfolioValue = tracker.getSharesHeldSoFar() * finalPrice;
        double overallReturn = tracker.getTotalCapitalInvestedSoFar() > 0
                ? ((portfolioValue - tracker.getTotalCapitalInvestedSoFar()) / tracker.getTotalCapitalInvestedSoFar()) * 100
                : 0;

        return SimulationResult.builder()
                .config(config)
                .totalDaysSimulated(totalDays)
                .partitionsCreated(partitionResults.size())
                .successfulPartitions(successCount)
                .killedPartitions(killCount)
                .neutralPartitions(neutralCount)
                .totalCapitalInvested(tracker.getTotalCapitalInvestedSoFar())
                .totalSharesAccumulated(tracker.getSharesHeldSoFar())
                .averagePricePaid(avgPrice)
                .finalMarketPrice(finalPrice)
                .finalPortfolioValue(portfolioValue)
                .overallReturnPct(overallReturn)
                .partitionResults(partitionResults)
                .dayResults(dayResults)
                .build();
    }

    private List<Double> generatePriceChanges(SimulationConfig config, Random random) {
        if (config.getScenario() == SimulationConfig.PriceScenario.CUSTOM
                && config.getCustomPriceChanges() != null) {
            return config.getCustomPriceChanges();
        }
        return new ArrayList<>();
    }

    private double generateDailyPriceChange(SimulationConfig.PriceScenario scenario, Random random) {
        return switch (scenario) {
            case BULL -> {
                // Positive bias: avg +0.15%, std dev 1.5%
                yield 0.15 + random.nextGaussian() * 1.5;
            }
            case BEAR -> {
                // Negative bias: avg -0.15%, std dev 1.5%
                yield -0.15 + random.nextGaussian() * 1.5;
            }
            case VOLATILE -> {
                // High volatility: avg 0%, std dev 3%
                yield random.nextGaussian() * 3.0;
            }
            case SIDEWAYS -> {
                // Low volatility: avg 0%, std dev 0.5%
                yield random.nextGaussian() * 0.5;
            }
            case RANDOM, CUSTOM, HISTORICAL -> {
                // Normal market: avg 0%, std dev 1.5%
                yield random.nextGaussian() * 1.5;
            }
        };
    }
}
