package com.dsip.backend.service;

import com.dsip.backend.mapper.DsipTrackerMapper;
import com.dsip.backend.dto.DsipTrackerDto;
import com.dsip.backend.dto.DsipTrackerUpdateDto;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.enums.TrackerStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DsipTrackerService {

        private final DsipTrackerMapper dsipTrackerMapper;

        @Transactional
        public DsipTracker createTracker(UUID userId, DsipTrackerDto dto) {

                DsipTracker tracker = DsipTracker.builder()
                                .userId(userId)
                                .stockSymbol(dto.getStockSymbol())
                                .convictionPeriodYears(dto.getConvictionPeriodYears())
                                .totalCapitalPlanned(dto.getTotalCapitalPlanned())
                                .partitionDays(dto.getPartitionDays())
                                .deploymentStyle(dto.getDeploymentStyle())
                                .baseConvictionScore(dto.getBaseConvictionScore())
                                .initialInvestedAmount(dto.getInitialInvestedAmount())
                                .initialSharesHeld(dto.getInitialSharesHeld())
                                .status(TrackerStatus.ACTIVE.getValue())
                                .currentPartitionIndex(1)
                                .createdAt(java.time.Instant.now())
                                .updatedAt(java.time.Instant.now())
                                .build();

                if (dsipTrackerMapper.existsByUserIdAndStockSymbol(userId, dto.getStockSymbol())) {
                        throw new com.dsip.backend.exception.DuplicateTrackerException(dto.getStockSymbol());
                }

                dsipTrackerMapper.insertTracker(tracker);

                LocalDate startDate = LocalDate.now();

                DsipPartition partition = DsipPartition.builder()
                                .trackerId(tracker.getTrackerId())
                                .partitionIndex(1)
                                .partitionStartDate(startDate)
                                .partitionDays(dto.getPartitionDays())
                                .partitionCapitalAllocated(dto.getTotalCapitalPlanned())
                                .successfulExecutionsCompleted(0)
                                .capitalDeployedSoFar(0)
                                .activeConvictionScore(dto.getBaseConvictionScore())
                                .status(PartitionStatus.ACTIVE.getValue())
                                .createdAt(java.time.Instant.now())
                                .updatedAt(java.time.Instant.now())
                                .build();

                dsipTrackerMapper.insertPartition(partition);

                return tracker;
        }

        public List<DsipTracker> getAllTrackers(UUID userId) {
                return dsipTrackerMapper.findAllTrackersByUserId(userId, null);
        }

        public Map<String, Object> getTrackerDetails(Integer trackerId, UUID userId) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new com.dsip.backend.exception.TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new com.dsip.backend.exception.UnauthorizedTrackerAccessException(trackerId, userId);
                }

                List<DsipPartition> partitions = dsipTrackerMapper.findPartitionsByTrackerId(trackerId);

                Map<String, Object> response = new HashMap<>();
                response.put("tracker", tracker);
                response.put("partitions", partitions);
                return response;
        }

        public com.dsip.backend.dto.TrackerDetailsDto getTrackerDetailsDto(Integer trackerId, UUID userId) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new com.dsip.backend.exception.TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new com.dsip.backend.exception.UnauthorizedTrackerAccessException(trackerId, userId);
                }

                List<DsipPartition> partitions = dsipTrackerMapper.findPartitionsByTrackerId(trackerId);

                return com.dsip.backend.dto.TrackerDetailsDto.builder()
                                .tracker(com.dsip.backend.dto.DsipTrackerDto.fromEntity(tracker))
                                .partitions(partitions.stream()
                                                .map(com.dsip.backend.dto.DsipPartitionDto::fromEntity)
                                                .toList())
                                .build();
        }

        @Transactional
        public void updateTracker(Integer trackerId, UUID userId, DsipTrackerUpdateDto dto) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new com.dsip.backend.exception.TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new com.dsip.backend.exception.UnauthorizedTrackerAccessException(trackerId, userId);
                }

                DsipPartition activePartition = dsipTrackerMapper.findActivePartitionByTrackerId(trackerId)
                                .orElse(null);

                boolean hasExecutions = activePartition != null
                                && activePartition.getSuccessfulExecutionsCompleted() > 0;

                // Block changes to critical fields if executions exist
                if (hasExecutions) {
                        // Logic to block updates to critical fields if needed
                }

                if (dto.getDeploymentStyle() != null) {
                        tracker.setDeploymentStyle(dto.getDeploymentStyle());
                }
                if (dto.getBaseConvictionScore() != null) {
                        tracker.setBaseConvictionScore(dto.getBaseConvictionScore());
                }
                if (dto.getStatus() != null) {
                        tracker.setStatus(dto.getStatus());
                }

                dsipTrackerMapper.updateTracker(tracker);
        }

        @Transactional
        public Map<String, Object> recordExecution(Integer trackerId, UUID userId,
                        com.dsip.backend.dto.DsipExecutionRequestDto dto) {
                // 1. Validate Tracker Ownership
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new com.dsip.backend.exception.TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new com.dsip.backend.exception.UnauthorizedTrackerAccessException(trackerId, userId);
                }

                // 2. Find Active Partition
                DsipPartition activePartition = dsipTrackerMapper.findActivePartitionByTrackerId(trackerId)
                                .orElseThrow(() -> new com.dsip.backend.exception.PartitionNotFoundException(
                                                trackerId));

                // 3. Insert Execution
                // TODO, figure out values of other attributes.
                com.dsip.backend.entity.DsipExecution execution = com.dsip.backend.entity.DsipExecution.builder()
                                .trackerId(trackerId)
                                .partitionId(activePartition.getPartitionId())
                                .executionDate(dto.getExecutionDate())
                                .lockInPercentage(dto.getLockInPercentage())
                                .convictionOverride(dto.getConvictionOverride())
                                .executedAmount(dto.getExecutedAmount())
                                .executionPrice(dto.getExecutionPrice())
                                .createdAt(java.time.Instant.now())
                                .build();

                dsipTrackerMapper.insertExecution(execution);

                // 4. Update Partition Metrics (Atomic)
                int currentExecutions = activePartition.getSuccessfulExecutionsCompleted() + 1;
                int targetExecutions = activePartition.getPartitionDays();

                boolean isComplete = currentExecutions >= targetExecutions;

                // Optimize: Single update for metrics, status, and end date
                int newStatus = isComplete ? com.dsip.backend.enums.PartitionStatus.COMPLETED.getValue()
                                : activePartition.getStatus();
                java.time.LocalDate endDate = isComplete ? dto.getExecutionDate() : null;

                // TODO: figure out values of other attributes to update.
                dsipTrackerMapper.updatePartitionMetrics(
                                activePartition.getPartitionId(),
                                dto.getExecutedAmount(),
                                newStatus,
                                endDate,
                                activePartition.getActiveConvictionScore(), 0, 0);

                if (isComplete) {
                        // TODO: how much partitions are we allowed to create,
                        // figure out max partitions and close the DSIP tracker here.

                        // Create Next Partition (Infinite)
                        DsipPartition nextPartition = DsipPartition.builder()
                                        .trackerId(trackerId)
                                        .partitionIndex(activePartition.getPartitionIndex() + 1)
                                        // Next start date is next day after execution
                                        .partitionStartDate(dto.getExecutionDate().plusDays(1))
                                        .partitionDays(tracker.getPartitionDays())
                                        .partitionCapitalAllocated(tracker.getTotalCapitalPlanned())
                                        .successfulExecutionsCompleted(0)
                                        .capitalDeployedSoFar(0)
                                        .activeConvictionScore(tracker.getBaseConvictionScore())
                                        .status(PartitionStatus.ACTIVE.getValue())
                                        .createdAt(java.time.Instant.now())
                                        .updatedAt(java.time.Instant.now())
                                        .build();

                        dsipTrackerMapper.insertPartition(nextPartition);

                        // Increment tracker current partition index
                        tracker.setCurrentPartitionIndex(nextPartition.getPartitionIndex());
                        dsipTrackerMapper.updateTracker(tracker);
                }

                return Map.of(
                                "status", "EXECUTED",
                                "partition_completed", isComplete);
        }

        public List<com.dsip.backend.dto.DsipExecutionDto> getRecentExecutions(Integer trackerId, UUID userId,
                        Integer limit) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new com.dsip.backend.exception.TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new com.dsip.backend.exception.UnauthorizedTrackerAccessException(trackerId, userId);
                }

                List<com.dsip.backend.entity.DsipExecution> executions = dsipTrackerMapper.findExecutionsByTrackerId(
                                trackerId,
                                limit);

                return executions.stream()
                                .map(com.dsip.backend.dto.DsipExecutionDto::fromEntity)
                                .toList();
        }
}
