package com.dsip.backend.service;

import com.dsip.backend.dto.RecommendationResponseDto;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.entity.Stock;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.exception.PartitionNotFoundException;
import com.dsip.backend.exception.StockNotFoundException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.exception.UnauthorizedTrackerAccessException;
import com.dsip.backend.mapper.DsipTrackerMapper;
import com.dsip.backend.mapper.StockMapper;
import com.dsip.backend.model.PartitionExecutionPlan;
import com.dsip.backend.service.DsipCalculationEngine.CalculationContext;
import com.dsip.backend.service.DsipCalculationEngine.InvestmentRecommendation;
import com.dsip.backend.util.FinancialCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for calculating investment recommendations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

        private final DsipTrackerMapper dsipTrackerMapper;
        private final StockMapper stockMapper;
        private final DsipCalculationEngine calculationEngine;
        private final FinancialCalculator financialCalculator;
        private final DsipTrackerService dsipTrackerService;
        private final PartitionAllocationPolicy partitionAllocationPolicy;

        /**
         * Get partition execution plan for a tracker.
         *
         * @param trackerId tracker id
         * @param userId user id
         * @return list of partition execution plan (partitionNumber, phaseNumber, allocatedAmount)
         */
        public java.util.List<PartitionExecutionPlan> getPartitionExecutionPlan(Integer trackerId, java.util.UUID userId) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerDetailsById(trackerId, userId);
                if (tracker == null) {
                        dsipTrackerMapper.findTrackerById(trackerId)
                                        .orElseThrow(() -> new TrackerNotFoundException(trackerId));
                        throw new UnauthorizedTrackerAccessException(trackerId, userId);
                }
                return partitionAllocationPolicy.generatePartitionExecutionPlan(tracker);
        }

        /**
         * Calculate investment recommendation for a tracker.
         *
         * @param trackerId the tracker ID
         * @param userId    the user ID (for authorization)
         * @param lockInPct the lock-in percentage (e.g., -5.0 for 5% below prev close)
         * @return recommendation response with breakdown
         */
        public RecommendationResponseDto getRecommendation(Integer trackerId, UUID userId, Double lockInPct) {
                // 1. Validate tracker ownership
                DsipTracker tracker = dsipTrackerMapper.findTrackerDetailsById(trackerId, userId);
                if (tracker == null) {
                        // Check if tracker exists at all
                        dsipTrackerMapper.findTrackerById(trackerId)
                                        .orElseThrow(() -> new TrackerNotFoundException(trackerId));
                        throw new UnauthorizedTrackerAccessException(trackerId, userId);
                }

                // 2. Get active partition
                DsipPartition activePartition = dsipTrackerMapper
                                .findPartitionByTrackerIdAndIndex(trackerId, tracker.getActivePartitionIndex())
                                .orElseThrow(() -> new PartitionNotFoundException(trackerId));

                // 3. Check if partition is active
                if (activePartition.getStatus() != PartitionStatus.ACTIVE.getValue()) {
                        log.warn("Partition {} is not active (status={}), cannot calculate recommendation",
                                        activePartition.getPartitionIndex(), activePartition.getStatus());
                        return buildInactivePartitionResponse(trackerId, activePartition);
                }

                // 4. Get stock info for current price and stock type
                Stock stock = stockMapper.findById(Long.valueOf(tracker.getStockId()))
                                .orElseThrow(() -> new StockNotFoundException(String.valueOf(tracker.getStockId())));

                // 5. Get current market price
                double currentMarketPrice = dsipTrackerService.getLatestMarketPrice(trackerId);

                // 6. Determine stock type (default to MIDCAP if not set)
                StockType stockType = stock.getStockType() != null
                                ? stock.getStockType()
                                : StockType.MIDCAP;

                // 7. Get conviction score (use override if execution provides one, otherwise
                // base)
                int convictionScore = tracker.getBaseConvictionScore() != null
                                ? tracker.getBaseConvictionScore()
                                : 90; // Default

                // 8. Build calculation context
                CalculationContext context = CalculationContext.builder()
                                .tracker(tracker)
                                .partition(activePartition)
                                .executionPrice(currentMarketPrice) // Use current price for avg deviation calculation
                                .currentMarketPrice(currentMarketPrice)
                                .lockInPct(lockInPct)
                                .convictionScore(convictionScore)
                                .stockType(stockType)
                                .build();

                // 9. Calculate recommendation
                InvestmentRecommendation recommendation = calculationEngine.calculateDailyInvestment(context);

                // 10. Calculate additional metrics for response
                double partitionProgress = financialCalculator.calculatePartitionProgressPercentage(activePartition,
                                currentMarketPrice, stockType);
                double returnProgress = financialCalculator.calculateReturnProgressPercentage(activePartition,
                                currentMarketPrice, stockType);
                double growthProgress = financialCalculator.calculateGrowthPersistencePercentage(activePartition); // Returns
                                                                                                                   // %
                double timeProgress = financialCalculator.calculateTimeProgressPercentage(activePartition); // Returns %
                double capitalProgress = financialCalculator.calculateCapitalProgressPercentage(activePartition); // Returns
                                                                                                                  // %
                double cumulativeReturnPct = financialCalculator.cumulativeReturnPercentage(
                                activePartition.getNoOfSharesBought(), activePartition.getCapitalInvestedSoFar(),
                                currentMarketPrice);

                double capitalDeployed = activePartition.getCapitalInvestedSoFar() != null
                                ? activePartition.getCapitalInvestedSoFar()
                                : 0.0;
                double capitalRemaining = activePartition.getPartitionCapitalAllocated() - capitalDeployed;

                // 11. Build response
                return RecommendationResponseDto.builder()
                                .trackerId(trackerId)
                                .recommendedAmount(recommendation.getRecommendedAmount())
                                .breakdown(RecommendationResponseDto.Breakdown.builder()
                                                .neutralCapital(recommendation.getNeutralCapital())
                                                .opportunityMultiplier(recommendation.getOpportunityMultiplier())
                                                .contingencyMultiplier(recommendation.getContingencyMultiplier())
                                                .finalMultiplier(recommendation.getFinalMultiplier())
                                                .build())
                                .signals(RecommendationResponseDto.Signals.builder()
                                                .avgHoldingPrice(recommendation.getSignals().getAvgHoldingPrice())
                                                .avgDeviationPct(recommendation.getSignals().getAvgDeviationPct())
                                                .avgSignal(recommendation.getSignals().getAvgSignal())
                                                .lockInPct(recommendation.getSignals().getLockInPct())
                                                .lockInSignal(recommendation.getSignals().getLockInSignal())
                                                .rawOpportunitySignal(
                                                                recommendation.getSignals().getRawOpportunitySignal())
                                                .convictionAmplifier(
                                                                recommendation.getSignals().getConvictionAmplifier())
                                                .isAbnormalDip(recommendation.isAbnormalDip())
                                                .build())
                                .partitionStatus(RecommendationResponseDto.PartitionStatusInfo.builder()
                                                .partitionIndex(activePartition.getPartitionIndex())
                                                .partitionProgressPct(
                                                                financialCalculator.round(partitionProgress, 2))
                                                .returnProgressPct(financialCalculator.round(returnProgress, 2))
                                                .growthPersistencePct(financialCalculator.round(growthProgress, 2))
                                                .timeProgressPct(financialCalculator.round(timeProgress, 2))
                                                .capitalProgressPct(financialCalculator.round(capitalProgress, 2))
                                                .capitalDeployed(financialCalculator.round(capitalDeployed, 2))
                                                .capitalRemaining(financialCalculator.round(capitalRemaining, 2))
                                                .cumulativeReturnPct(financialCalculator.round(cumulativeReturnPct, 2))
                                                .build())
                                .build();
        }


        public RecommendationResponseDto getRecommendationForSimulation(Integer trackerId, UUID userId, Double lockInPct) {
                // 1. Validate tracker ownership
                DsipTracker tracker = dsipTrackerMapper.findTrackerDetailsById(trackerId, userId);
                if (tracker == null) {
                        // Check if tracker exists at all
                        dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new TrackerNotFoundException(trackerId));
                        throw new UnauthorizedTrackerAccessException(trackerId, userId);
                }

                // 2. Get active partition
                DsipPartition activePartition = dsipTrackerMapper
                        .findPartitionByTrackerIdAndIndex(trackerId, tracker.getActivePartitionIndex())
                        .orElseThrow(() -> new PartitionNotFoundException(trackerId));

                // 3. Check if partition is active
                if (activePartition.getStatus() != PartitionStatus.ACTIVE.getValue()) {
                        log.warn("Partition {} is not active (status={}), cannot calculate recommendation",
                                activePartition.getPartitionIndex(), activePartition.getStatus());
                        return buildInactivePartitionResponse(trackerId, activePartition);
                }

                // 4. Get stock info for current price and stock type
                Stock stock = stockMapper.findById(Long.valueOf(tracker.getStockId()))
                        .orElseThrow(() -> new StockNotFoundException(String.valueOf(tracker.getStockId())));

                // 5. Get current market price
                double currentMarketPrice = dsipTrackerService.getLatestMarketPrice(trackerId);

                // 6. Determine stock type (default to MIDCAP if not set)
                StockType stockType = stock.getStockType() != null
                        ? stock.getStockType()
                        : StockType.MIDCAP;

                // 7. Get conviction score (use override if execution provides one, otherwise
                // base)
                int convictionScore = tracker.getBaseConvictionScore() != null
                        ? tracker.getBaseConvictionScore()
                        : 90; // Default

                // 8. Build calculation context
                CalculationContext context = CalculationContext.builder()
                        .tracker(tracker)
                        .partition(activePartition)
                        .executionPrice(currentMarketPrice) // Use current price for avg deviation calculation
                        .currentMarketPrice(currentMarketPrice)
                        .lockInPct(lockInPct)
                        .convictionScore(convictionScore)
                        .stockType(stockType)
                        .build();

                // 9. Calculate recommendation
                InvestmentRecommendation recommendation = calculationEngine.calculateDailyInvestmentForSimulation(context);

                // 10. Calculate additional metrics for response
                double partitionProgress = financialCalculator.calculatePartitionProgressPercentage(activePartition,
                        currentMarketPrice, stockType);
                double returnProgress = financialCalculator.calculateReturnProgressPercentage(activePartition,
                        currentMarketPrice, stockType);
                double growthProgress = financialCalculator.calculateGrowthPersistencePercentage(activePartition); // Returns
                // %
                double timeProgress = financialCalculator.calculateTimeProgressPercentage(activePartition); // Returns %
                double capitalProgress = financialCalculator.calculateCapitalProgressPercentage(activePartition); // Returns
                // %
                double cumulativeReturnPct = financialCalculator.cumulativeReturnPercentage(
                        activePartition.getNoOfSharesBought(), activePartition.getCapitalInvestedSoFar(),
                        currentMarketPrice);

                double capitalDeployed = activePartition.getCapitalInvestedSoFar() != null
                        ? activePartition.getCapitalInvestedSoFar()
                        : 0.0;
                double capitalRemaining = activePartition.getPartitionCapitalAllocated() - capitalDeployed;

                // 11. Build response
                return RecommendationResponseDto.builder()
                        .trackerId(trackerId)
                        .recommendedAmount(recommendation.getRecommendedAmount())
                        .breakdown(RecommendationResponseDto.Breakdown.builder()
                                .neutralCapital(recommendation.getNeutralCapital())
                                .opportunityMultiplier(recommendation.getOpportunityMultiplier())
                                .contingencyMultiplier(recommendation.getContingencyMultiplier())
                                .finalMultiplier(recommendation.getFinalMultiplier())
                                .build())
                        .signals(RecommendationResponseDto.Signals.builder()
                                .avgHoldingPrice(recommendation.getSignals().getAvgHoldingPrice())
                                .avgDeviationPct(recommendation.getSignals().getAvgDeviationPct())
                                .avgSignal(recommendation.getSignals().getAvgSignal())
                                .lockInPct(recommendation.getSignals().getLockInPct())
                                .lockInSignal(recommendation.getSignals().getLockInSignal())
                                .rawOpportunitySignal(
                                        recommendation.getSignals().getRawOpportunitySignal())
                                .convictionAmplifier(
                                        recommendation.getSignals().getConvictionAmplifier())
                                .isAbnormalDip(recommendation.isAbnormalDip())
                                .build())
                        .partitionStatus(RecommendationResponseDto.PartitionStatusInfo.builder()
                                .partitionIndex(activePartition.getPartitionIndex())
                                .partitionProgressPct(
                                        financialCalculator.round(partitionProgress, 2))
                                .returnProgressPct(financialCalculator.round(returnProgress, 2))
                                .growthPersistencePct(financialCalculator.round(growthProgress, 2))
                                .timeProgressPct(financialCalculator.round(timeProgress, 2))
                                .capitalProgressPct(financialCalculator.round(capitalProgress, 2))
                                .capitalDeployed(financialCalculator.round(capitalDeployed, 2))
                                .capitalRemaining(financialCalculator.round(capitalRemaining, 2))
                                .cumulativeReturnPct(financialCalculator.round(cumulativeReturnPct, 2))
                                .build())
                        .build();
        }


        /**
         * Build response for inactive partition (no recommendation available).
         */
        private RecommendationResponseDto buildInactivePartitionResponse(Integer trackerId, DsipPartition partition) {
                double capitalDeployed = partition.getCapitalInvestedSoFar() != null
                                ? partition.getCapitalInvestedSoFar()
                                : 0.0;
                double capitalRemaining = partition.getPartitionCapitalAllocated() - capitalDeployed;

                return RecommendationResponseDto.builder()
                                .trackerId(trackerId)
                                .recommendedAmount(0.0)
                                .partitionStatus(RecommendationResponseDto.PartitionStatusInfo.builder()
                                                .partitionIndex(partition.getPartitionIndex())
                                                .capitalDeployed(capitalDeployed)
                                                .capitalRemaining(capitalRemaining)
                                                .build())
                                .build();
        }
}
