package com.dsip.backend.service;

import com.dsip.backend.dto.DsipExecutionRequestDto;
import com.dsip.backend.dto.ExecutionResponseDto;
import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.exception.PartitionNotFoundException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.mapper.DsipTrackerMapper;
import com.dsip.backend.model.PartitionEndDecision;
import com.dsip.backend.model.PartitionPlan;
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
        private final PartitionAllocationPolicy allocationPolicy;
        private final PartitionLifecyclePolicy lifecyclePolicy;
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

                // 3. Get Last Execution Price (before inserting current one)
                List<com.dsip.backend.dto.TrackerDetailsDto.HistoryItem> recentExecutions = dsipTrackerMapper
                                .findExecutionHistoryByTrackerId(trackerId, 1);

                Double lastExecutionPrice = 0.0;
                if (!recentExecutions.isEmpty()) {
                        lastExecutionPrice = recentExecutions.get(0).getExecutedPrice();
                }

                double latestMarketPrice = dsipTrackerService.getLatestMarketPrice(trackerId);

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
                applyExecutionToPartition(activePartition, dto, lastExecutionPrice, latestMarketPrice);
                // Apply execution to tracker (in-memory)
                applyExecutionToTracker(tracker, dto);

                // 6. Evaluate Lifecycle

                PartitionEndDecision decision = lifecyclePolicy.evaluate(tracker, activePartition, latestMarketPrice);
                if (decision.isShouldEnd()) {
                        // Mark current partition as completed in memory
                        activePartition.setStatus(decision.getReason().toPartitionStatus().getValue());
                        activePartition.setPartitionEndDate(Instant.now());

                        if (decision.getReason().toPartitionStatus() == PartitionStatus.COMPLETED) {
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
                                                .status(PartitionStatus.ACTIVE.getValue())
                                                .createdAt(Instant.now())
                                                .build();

                                dsipTrackerMapper.insertPartition(nextPartition);
                                tracker.setActivePartitionIndex(nextPartitionIndex);

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
         * Updates capital invested, shares bought,negative deviation and growth count
         * DOES NOT UPDATE DB
         */
        private void applyExecutionToPartition(DsipPartition partition, DsipExecutionRequestDto dto,
                        Double lastExecutionPrice, Double marketPrice) {
                Double sharesBought = financialCalculator.calculateSharesBought(dto.getExecutedAmount(),
                                dto.getExecutionPrice());

                partition.setCapitalInvestedSoFar(partition.getCapitalInvestedSoFar() + dto.getExecutedAmount());
                partition.setNoOfSharesBought(partition.getNoOfSharesBought() + sharesBought);

                boolean isGrowth = financialCalculator.calculateIsGrowth(partition, dto.getExecutionPrice(), marketPrice);

                if (isGrowth) {
                        partition.setSuccessfulGrowthCount(partition.getSuccessfulGrowthCount() + 1);
                }

                double deviation = dto.getExecutionPrice() - marketPrice;
                if (deviation < 0) {
                        double newAverage = (partition.getAvgNegativeDeviation() * partition.getNegativeDeviationCount()
                                        + deviation) / (partition.getNegativeDeviationCount() + 1);
                        partition.setAvgNegativeDeviation(newAverage);
                        partition.setNegativeDeviationCount(partition.getNegativeDeviationCount() + 1);
                        if (deviation < partition.getMaxNegativeDeviation()) {
                                partition.setMaxNegativeDeviation(deviation);
                        }
                }
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
}
