package com.dsip.backend.service;

import com.dsip.backend.dto.DsipExecutionRequestDto;
import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.exception.PartitionNotFoundException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.exception.UnauthorizedTrackerAccessException;
import com.dsip.backend.mapper.DsipTrackerMapper;
import com.dsip.backend.model.PartitionEndDecision;
import com.dsip.backend.model.PartitionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final DsipTrackerMapper dsipTrackerMapper;
    private final PartitionAllocationPolicy allocationPolicy;
    private final PartitionLifecyclePolicy lifecyclePolicy;
    private final com.dsip.backend.util.FinancialCalculator financialCalculator;

    @Transactional
    public Map<String, Object> executeTrade(Integer trackerId, UUID userId, DsipExecutionRequestDto dto) {
        // 1. Validate Tracker Ownership
        DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

        if (!tracker.getUserId().equals(userId)) {
            throw new UnauthorizedTrackerAccessException(trackerId, userId);
        }

        // 2. Find Active Partition
        DsipPartition activePartition = dsipTrackerMapper.findActivePartitionByTrackerId(trackerId)
                .orElseThrow(() -> new PartitionNotFoundException(trackerId));

        // 3. Insert Execution
        // Both executed_amount and execution_price are in rupees
        int sharesBought = dto.getExecutionPrice() > 0 ? dto.getExecutedAmount() / dto.getExecutionPrice() : 0;
        boolean isGrowth = determineGrowth(activePartition, dto);

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

        // 4. Update Partition Metrics
        dsipTrackerMapper.updatePartitionAfterExecution(
                activePartition.getPartitionId(),
                dto.getExecutedAmount(),
                sharesBought,
                isGrowth);

        // Refresh partition state after update
        activePartition.setCapitalInvestedSoFar(
                activePartition.getCapitalInvestedSoFar() + dto.getExecutedAmount());
        activePartition.setNoOfSharesBought(
                activePartition.getNoOfSharesBought() + sharesBought);
        if (isGrowth) {
            activePartition.setSuccessfulGrowthCount(
                    activePartition.getSuccessfulGrowthCount() + 1);
        }

        // 5. Update Tracker Aggregates
        dsipTrackerMapper.updateTrackerAggregates(
                trackerId,
                dto.getExecutedAmount(),
                sharesBought,
                tracker.getActivePartitionIndex());

        // 6. Evaluate Lifecycle
        List<DsipExecution> partitionExecutions = dsipTrackerMapper.findExecutionsByPartitionId(
                activePartition.getPartitionId());

        PartitionEndDecision decision = lifecyclePolicy.evaluate(tracker, activePartition, partitionExecutions);

        boolean partitionCompleted = false;
        if (decision.isShouldEnd()) {
            // Close current partition
            dsipTrackerMapper.closePartition(activePartition.getPartitionId(), Instant.now());
            partitionCompleted = true;

            // Create next partition
            List<DsipPartition> completed = dsipTrackerMapper.findCompletedPartitions(trackerId);
            List<Integer> pastPartitionLengths = completed.stream()
                    .map(p -> financialCalculator.calculateDaysBetween(p.getCreatedAt(),
                    p.getPartitionEndDate()))
                    .filter(d -> d > 0)
                    .collect(java.util.stream.Collectors.toList());
            int nextPartitionIndex = activePartition.getPartitionIndex() + 1;
            PartitionPlan plan = allocationPolicy.createPlan(tracker, nextPartitionIndex,
                    pastPartitionLengths);

            DsipPartition nextPartition = DsipPartition.builder()
                    .trackerId(trackerId)
                    .partitionIndex(plan.getPartitionIndex())
                    .expectedPartitionDays(plan.getExpectedLengthDays())
                    .partitionCapitalAllocated(plan.getAllocatedCapital())
                    .capitalInvestedSoFar(0)
                    .noOfSharesBought(0)
                    .successfulGrowthCount(0)
                    .status(PartitionStatus.ACTIVE.getValue())
                    .createdAt(Instant.now())
                    .build();

            dsipTrackerMapper.insertPartition(nextPartition);

            // Update tracker's active partition index
            dsipTrackerMapper.updateTrackerAggregates(
                    trackerId,
                    0,
                    0,
                    nextPartitionIndex);

            log.info("Partition {} ended with reason {}, created partition {}",
                    activePartition.getPartitionIndex(), decision.getReason(), nextPartitionIndex);
        }

        return Map.of(
                "status", "EXECUTED",
                "partition_completed", partitionCompleted,
                "end_reason", decision.getReason() != null ? decision.getReason().name() : "N/A");
    }

    private boolean determineGrowth(DsipPartition partition, DsipExecutionRequestDto dto) {
        // Growth is determined by comparing current price to average cost basis
        if (partition.getCapitalInvestedSoFar() == 0 || partition.getNoOfSharesBought() == 0) {
            return false;
        }
        int avgCostBasis = partition.getCapitalInvestedSoFar() / partition.getNoOfSharesBought();
        return dto.getExecutionPrice() > avgCostBasis;
    }
}
