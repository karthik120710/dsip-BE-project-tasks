package com.dsip.backend.service;

import com.dsip.backend.mapper.DsipTrackerMapper;
import com.dsip.backend.mapper.StockMapper;
import com.dsip.backend.dto.DsipTrackerDto;
import com.dsip.backend.dto.DsipTrackerUpdateDto;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.entity.Stock;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.enums.TrackerStatus;
import com.dsip.backend.exception.DuplicateTrackerException;
import com.dsip.backend.exception.StockNotFoundException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.exception.UnauthorizedTrackerAccessException;
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
public class DsipTrackerService {

    private final DsipTrackerMapper dsipTrackerMapper;
    private final StockMapper stockMapper;
    private final PartitionAllocationPolicy allocationPolicy;

    @Transactional
    public DsipTrackerDto createTracker(UUID userId, DsipTrackerDto dto) {
        // Look up stock by symbol
        Stock stock = stockMapper.findByStockSymbol(dto.getStockSymbol())
                .orElseThrow(() -> new StockNotFoundException("Stock with symbol '" + dto.getStockSymbol() + "' not found"));

        Integer stockId = stock.getId().intValue();

        // Check for duplicate tracker
        if (dsipTrackerMapper.existsByUserIdAndStockId(userId, stockId)) {
            throw new DuplicateTrackerException(stockId);
        }

        DsipTracker tracker = DsipTracker.builder()
                .userId(userId)
                .stockId(stockId)
                .convictionPeriodYears(dto.getConvictionPeriodYears())
                .totalCapitalPlanned(dto.getTotalCapitalPlanned())
                .partitionDays(dto.getPartitionDays())
                .deploymentStyle(dto.getDeploymentStyle())
                .baseConvictionScore(dto.getBaseConvictionScore())
                .initialInvestedAmount(dto.getInitialInvestedAmount())
                .initialSharesHeld(dto.getInitialSharesHeld())
                .status(TrackerStatus.ACTIVE.getValue())
                .activePartitionIndex(1)
                .totalCapitalInvestedSoFar(0)
                .sharesHeldSoFar(0)
                .isFractionalSharesAllowed(dto.getIsFractionalSharesAllowed() != null ? dto.getIsFractionalSharesAllowed() : false)
                .createdAt(Instant.now())
                .build();

        dsipTrackerMapper.insertTracker(tracker);

        // Create first partition using allocation policy
        PartitionPlan plan = allocationPolicy.createFirstPlan(tracker);

        DsipPartition partition = DsipPartition.builder()
                .trackerId(tracker.getTrackerId())
                .partitionIndex(plan.getPartitionIndex())
                .expectedPartitionDays(plan.getExpectedLengthDays())
                .partitionCapitalAllocated(plan.getAllocatedCapital())
                .capitalInvestedSoFar(0)
                .noOfSharesBought(0)
                .successfulGrowthCount(0)
                .status(PartitionStatus.ACTIVE.getValue())
                .createdAt(Instant.now())
                .build();

        dsipTrackerMapper.insertPartition(partition);

        return DsipTrackerDto.fromEntity(tracker, stock.getStockSymbol());
    }

    public List<DsipTrackerDto> getAllTrackers(UUID userId) {
        List<DsipTracker> trackers = dsipTrackerMapper.findAllTrackersByUserId(userId, null);
        return trackers.stream()
                .map(this::toTrackerDto)
                .toList();
    }

    public com.dsip.backend.dto.TrackerDetailsDto getTrackerDetailsDto(Integer trackerId, UUID userId) {
        DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

        if (!tracker.getUserId().equals(userId)) {
            throw new UnauthorizedTrackerAccessException(trackerId, userId);
        }

        List<DsipPartition> partitions = dsipTrackerMapper.findPartitionsByTrackerId(trackerId);

        return com.dsip.backend.dto.TrackerDetailsDto.builder()
                .tracker(toTrackerDto(tracker))
                .partitions(partitions.stream()
                        .map(com.dsip.backend.dto.DsipPartitionDto::fromEntity)
                        .toList())
                .build();
    }

    @Transactional
    public void updateTracker(Integer trackerId, UUID userId, DsipTrackerUpdateDto dto) {
        DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

        if (!tracker.getUserId().equals(userId)) {
            throw new UnauthorizedTrackerAccessException(trackerId, userId);
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

    public List<com.dsip.backend.dto.DsipExecutionDto> getRecentExecutions(Integer trackerId, UUID userId, Integer limit) {
        DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

        if (!tracker.getUserId().equals(userId)) {
            throw new UnauthorizedTrackerAccessException(trackerId, userId);
        }

        List<com.dsip.backend.entity.DsipExecution> executions = dsipTrackerMapper.findExecutionsByTrackerId(
                trackerId,
                limit);

        return executions.stream()
                .map(com.dsip.backend.dto.DsipExecutionDto::fromEntity)
                .toList();
    }

    private DsipTrackerDto toTrackerDto(DsipTracker tracker) {
        String stockSymbol = stockMapper.findById(tracker.getStockId().longValue())
                .map(Stock::getStockSymbol)
                .orElse(null);
        return DsipTrackerDto.fromEntity(tracker, stockSymbol);
    }
}
