package com.dsip.backend.service;

import com.dsip.backend.dto.DsipExecutionRequestDto;
import com.dsip.backend.dto.ExecutionResponseDto;
import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.entity.Stock;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.exception.PartitionNotFoundException;
import com.dsip.backend.exception.StockNotFoundException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.mapper.DsipTrackerMapper;
import com.dsip.backend.mapper.StockMapper;
import com.dsip.backend.model.PartitionEndDecision;
import com.dsip.backend.model.PartitionPlan;
import com.dsip.backend.service.DsipCalculationEngine.CalculationContext;
import com.dsip.backend.service.DsipCalculationEngine.LifecycleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

        private final DsipTrackerMapper dsipTrackerMapper;
        private final StockMapper stockMapper;
        private final PartitionAllocationPolicy allocationPolicy;
        private final PartitionLifecyclePolicy lifecyclePolicy;
        private final DsipCalculationEngine calculationEngine;
        private final com.dsip.backend.util.FinancialCalculator financialCalculator;
        private final DsipTrackerService dsipTrackerService;

        @Transactional
        public ExecutionResponseDto executeTrade(Integer trackerId, UUID userId, DsipExecutionRequestDto dto) {
                // 1. Validate Tracker Ownership
                DsipTracker tracker = dsipTrackerMapper.findTrackerDetailsById(trackerId, userId);
                if (tracker == null) {
                        throw new TrackerNotFoundException(trackerId);
                }

                // 2. Find Active Partition using tracker's active partition index
                DsipPartition activePartition = dsipTrackerMapper
                                .findPartitionByTrackerIdAndIndex(trackerId, tracker.getActivePartitionIndex())
                                .orElseThrow(() -> new PartitionNotFoundException(trackerId));

                // Check if partition is already ended
                if (activePartition.getStatus() != PartitionStatus.ACTIVE.getValue()) {
                        PartitionStatus status = PartitionStatus.fromValue(activePartition.getStatus());
                        EndReason reason = EndReason.fromPartitionStatus(status);
                        return ExecutionResponseDto.builder()
                                        .status("SKIPPED")
                                        .endReason(reason)
                                        .build();
                }

                // 3. Get stock info for yesterday's close price
                Stock stock = stockMapper.findById(Long.valueOf(tracker.getStockId()))
                                .orElseThrow(() -> new StockNotFoundException(String.valueOf(tracker.getStockId())));

                // Yesterday's price = previous day's closing price from stock table
                Double yesterdayPrice = stock.getLastDateMarketClosingPrice();

                // Today's market price = latest price (from stock or recent execution)
                double todayMarketPrice = dsipTrackerService.getLatestMarketPrice(trackerId);

                // 4. Insert Execution
                DsipExecution execution = DsipExecution.builder()
                                .trackerId(trackerId)
                                .partitionId(activePartition.getPartitionId())
                                .lockInPercentage(dto.getLockInPercentage())
                                .convictionOverride(dto.getConvictionOverride())
                                .executedAmount(dto.getExecutedAmount())
                                .executionPrice(dto.getExecutionPrice())
                                .createdAt(Instant.now())
                                .build();

                dsipTrackerMapper.insertExecution(execution);

                // Apply execution to partition (in-memory)
                // Pass todayPrice and yesterdayPrice for growth day calculation
                applyExecutionToPartition(activePartition, dto, todayMarketPrice, yesterdayPrice);
                // Apply execution to tracker (in-memory)
                applyExecutionToTracker(tracker, dto);

                // 6. Evaluate Lifecycle
                PartitionEndDecision decision = lifecyclePolicy.evaluate(tracker, activePartition, todayMarketPrice);
                if (decision.isShouldEnd()) {
                        // Mark current partition as ended in memory
                        activePartition.setStatus(decision.getReason().toPartitionStatus().getValue());
                        activePartition.setPartitionEndDate(Instant.now());

                        // Check if we should create next partition
                        // Now applies to all end statuses (SUCCESS, KILL_SWITCH, NEUTRAL)
                        boolean shouldCreateNext = shouldCreateNextPartition(tracker);

                        if (shouldCreateNext) {
                                int nextPartitionIndex = activePartition.getPartitionIndex() + 1;
                                List<DsipPartition> completed = dsipTrackerMapper.findCompletedPartitions(trackerId);
                                List<Integer> pastPartitionLengths = completed.stream()
                                                .map(p -> financialCalculator.calculateDaysBetween(p.getCreatedAt(),
                                                                p.getPartitionEndDate()))
                                                .filter(d -> d > 0)
                                                .collect(java.util.stream.Collectors.toList());

                                PartitionPlan plan = allocationPolicy.createPlan(tracker, nextPartitionIndex,
                                                pastPartitionLengths);

                                DsipPartition nextPartition = DsipPartition.builder()
                                                .trackerId(trackerId)
                                                .partitionIndex(plan.getPartitionIndex())
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

                                dsipTrackerMapper.insertPartition(nextPartition);
                                tracker.setActivePartitionIndex(nextPartitionIndex);

                                log.info("Created partition {} after {} for tracker {}",
                                                nextPartitionIndex, decision.getReason(), trackerId);
                        } else {
                                // No more capital or conviction period ended - mark tracker as completed
                                tracker.setStatus(com.dsip.backend.enums.TrackerStatus.COMPLETED.getValue());
                                log.info("Tracker {} completed after partition {} ended with {}",
                                                trackerId, activePartition.getPartitionIndex(), decision.getReason());
                        }
                }

                // Final Persist of State to Database
                dsipTrackerMapper.updatePartition(activePartition);
                dsipTrackerMapper.updateTracker(tracker);

                return ExecutionResponseDto.builder()
                                .status("EXECUTED")
                                .endReason(decision.getReason())
                                .build();
        }

        /**
         * Apply execution metrics to partition in memory.
         * Updates capital invested, shares bought, negative deviation and growth count.
         * DOES NOT UPDATE DB
         *
         * @param partition      the partition to update
         * @param dto            execution request data
         * @param todayPrice     today's market price (current price)
         * @param yesterdayPrice yesterday's closing price (prev close)
         */
        private void applyExecutionToPartition(DsipPartition partition, DsipExecutionRequestDto dto,
                        Double todayPrice, Double yesterdayPrice) {
                Double sharesBought = financialCalculator.calculateSharesBought(dto.getExecutedAmount(),
                                dto.getExecutionPrice());

                partition.setCapitalInvestedSoFar(partition.getCapitalInvestedSoFar() + dto.getExecutedAmount());
                partition.setNoOfSharesBought(partition.getNoOfSharesBought() + sharesBought);

                // Calculate cumulative return after this execution
                double cumulativeReturnPct = calculationEngine.calculateCumulativeReturnPct(partition, todayPrice);

                // NEW: Growth day definition from spec
                // Condition: todayPrice > yesterdayPrice AND cumulativeReturn > 0
                boolean isGrowth = calculationEngine.isSuccessfulGrowthDay(
                                todayPrice,
                                yesterdayPrice != null ? yesterdayPrice : todayPrice,
                                cumulativeReturnPct);

                if (isGrowth) {
                        int currentGrowthCount = partition.getSuccessfulGrowthCount() != null
                                        ? partition.getSuccessfulGrowthCount()
                                        : 0;
                        partition.setSuccessfulGrowthCount(currentGrowthCount + 1);
                }

                // Calculate average price deviation for negative deviation tracking
                double avgHoldingPrice = calculationEngine.calculateAverageHoldingPrice(partition, dto.getExecutionPrice());
                double avgDeviationPct = calculationEngine.calculateAverageDeviation(avgHoldingPrice, dto.getExecutionPrice());

                // Update negative deviation stats using calculation engine
                calculationEngine.updateNegativeDeviationStats(partition, avgDeviationPct);
        }

        /**
         * Apply execution metrics to tracker in memory.
         * Updates total capital invested and shares held.
         * DOES NOT UPDATE DB
         */
        private void applyExecutionToTracker(DsipTracker tracker, DsipExecutionRequestDto dto) {
                Double sharesBought = financialCalculator.calculateSharesBought(dto.getExecutedAmount(),
                                dto.getExecutionPrice());

                tracker.setTotalCapitalInvestedSoFar(tracker.getTotalCapitalInvestedSoFar() + dto.getExecutedAmount());
                tracker.setSharesHeldSoFar(tracker.getSharesHeldSoFar() + sharesBought);
        }

        /**
         * Determines if a next partition should be created after the current one ends.
         * Next partition is created if:
         * 1. There is remaining capital to invest
         * 2. Still within the conviction period
         *
         * @param tracker the DSIP tracker
         * @return true if next partition should be created
         */
        private boolean shouldCreateNextPartition(DsipTracker tracker) {
                // Check remaining capital
                double remainingCapital = tracker.getTotalCapitalPlanned() - tracker.getTotalCapitalInvestedSoFar();
                boolean hasRemainingCapital = remainingCapital > 0;

                if (!hasRemainingCapital) {
                        log.info("No remaining capital for tracker {} (invested: {}, planned: {})",
                                        tracker.getTrackerId(), tracker.getTotalCapitalInvestedSoFar(),
                                        tracker.getTotalCapitalPlanned());
                        return false;
                }

                // Check if within conviction period
                boolean withinConviction = calculationEngine.isWithinConvictionPeriod(tracker);

                if (!withinConviction) {
                        log.info("Tracker {} conviction period ended (created: {}, conviction years: {})",
                                        tracker.getTrackerId(), tracker.getCreatedAt(),
                                        tracker.getConvictionPeriodYears());
                        return false;
                }

                log.debug("Tracker {} can create next partition (remaining capital: {}, within conviction: true)",
                                tracker.getTrackerId(), remainingCapital);
                return true;
        }
}
