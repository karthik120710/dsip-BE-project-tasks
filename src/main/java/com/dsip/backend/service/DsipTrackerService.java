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
import com.dsip.backend.exception.StockNotFoundException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.exception.PartitionNotFoundException;
import com.dsip.backend.exception.UnauthorizedTrackerAccessException;
import com.dsip.backend.model.PartitionPlan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dsip.backend.util.FinancialCalculator;

@Service
@RequiredArgsConstructor
@Slf4j
public class DsipTrackerService {

        private final DsipTrackerMapper dsipTrackerMapper;
        private final StockMapper stockMapper;
        private final PartitionAllocationPolicy allocationPolicy;
        private final FinancialCalculator financialCalculator;

        @Transactional
        public DsipTrackerDto createTracker(UUID userId, DsipTrackerDto dto) {
                // Look up stock by symbol
                Stock stock = stockMapper.findByStockSymbol(dto.getStockSymbol())
                                .orElseThrow(
                                                () -> new StockNotFoundException("Stock with symbol '"
                                                                + dto.getStockSymbol() + "' not found"));

                Integer stockId = stock.getId().intValue();

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
                                .isFractionalSharesAllowed(
                                                dto.getIsFractionalSharesAllowed() != null
                                                                ? dto.getIsFractionalSharesAllowed()
                                                                : false)
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

        public com.dsip.backend.dto.PortfolioResponseDto getAllTrackers(UUID userId) {
                List<DsipTracker> trackers = dsipTrackerMapper.findAllTrackersWithStockByUserId(userId);

                // Initialize aggregates
                double totalMarketValue = 0.0;
                int totalInvestedCapital = 0;
                double dsipTotalMarketValue = 0.0;
                int dsipTotalInvestedCapital = 0;

                List<com.dsip.backend.dto.TrackerSummaryDto> trackerSummaries = new java.util.ArrayList<>();

                for (DsipTracker tracker : trackers) {
                        Double currentPrice = tracker.getCurrentPrice();

                        // Skip trackers without price data
                        if (currentPrice == null || currentPrice == 0.0) {
                                currentPrice = 0.0;
                        }

                        // Calculate total shares and invested amounts
                        int totalShares = tracker.getSharesHeldSoFar() + tracker.getInitialSharesHeld();
                        int totalInvested = tracker.getTotalCapitalInvestedSoFar() + tracker.getInitialInvestedAmount();

                        // Calculate market values
                        double trackerMarketValue = financialCalculator.calculateMarketValue(currentPrice, totalShares);
                        double dsipMarketValue = financialCalculator.calculateMarketValue(currentPrice,
                                        tracker.getSharesHeldSoFar());

                        // Calculate profit percentages
                        Double netProfitPercentage = financialCalculator.calculateProfitPercentage(trackerMarketValue,
                                        totalInvested);
                        Double dsipNetProfitPercentage = financialCalculator.calculateProfitPercentage(dsipMarketValue,
                                        tracker.getTotalCapitalInvestedSoFar());

                        // Build tracker summary
                        com.dsip.backend.dto.TrackerSummaryDto summary = com.dsip.backend.dto.TrackerSummaryDto
                                        .builder()
                                        .id(tracker.getTrackerId())
                                        .symbol(tracker.getStockSymbol())
                                        .name(tracker.getStockName())
                                        .totalCapitalInvestedSoFar(totalInvested)
                                        .totalCapitalPlanned(tracker.getTotalCapitalPlanned())
                                        .netProfitPercentage(netProfitPercentage)
                                        .dsipTotalInvestedCapital(tracker.getTotalCapitalInvestedSoFar())
                                        .dsipNetProfitPercentage(dsipNetProfitPercentage)
                                        .build();

                        trackerSummaries.add(summary);

                        // Aggregate portfolio metrics
                        totalMarketValue += trackerMarketValue;
                        totalInvestedCapital += totalInvested;
                        dsipTotalMarketValue += dsipMarketValue;
                        dsipTotalInvestedCapital += tracker.getTotalCapitalInvestedSoFar();
                }

                // Calculate portfolio-level profit percentages
                Double netProfitPercentage = financialCalculator.calculateProfitPercentage(totalMarketValue,
                                totalInvestedCapital);
                Double dsipNetProfitPercentage = financialCalculator.calculateProfitPercentage(dsipTotalMarketValue,
                                dsipTotalInvestedCapital);

                // Build and return portfolio response
                return com.dsip.backend.dto.PortfolioResponseDto.builder()
                                .totalMarketValue(financialCalculator.round(totalMarketValue, 2))
                                .totalInvestedCapital(totalInvestedCapital)
                                .netProfitPercentage(netProfitPercentage)
                                .dsipTotalMarketValue(financialCalculator.round(dsipTotalMarketValue, 2))
                                .dsipTotalInvestedCapital(dsipTotalInvestedCapital)
                                .dsipTotalNetProfitPercentage(dsipNetProfitPercentage)
                                .dsipTrackers(trackerSummaries)
                                .build();
        }

        public com.dsip.backend.dto.TrackerDetailsDto getTrackerDetailsDto(Integer trackerId, UUID userId) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerDetailsById(trackerId, userId);

                if (tracker == null) {
                        // Check if it exists at all to throw correct exception
                        dsipTrackerMapper.findTrackerById(trackerId)
                                        .orElseThrow(() -> new TrackerNotFoundException(trackerId));
                        throw new UnauthorizedTrackerAccessException(trackerId, userId);
                }

                double currentPrice = tracker.getCurrentPrice() != null ? tracker.getCurrentPrice() : 0.0;

                // --- Overall Performance (Includes Initials) ---
                int totalShares = tracker.getSharesHeldSoFar() + tracker.getInitialSharesHeld();
                int totalInvested = tracker.getTotalCapitalInvestedSoFar() + tracker.getInitialInvestedAmount();

                double currentTotalValue = financialCalculator.calculateMarketValue(currentPrice, totalShares);
                Double netProfitPercentage = financialCalculator.calculateProfitPercentage(currentTotalValue,
                                totalInvested);

                // --- DSIP Specific Performance (Excludes Initials) ---
                double dsipTotalMarketValue = financialCalculator.calculateMarketValue(currentPrice,
                                tracker.getSharesHeldSoFar());
                Integer dsipTotalInvestedCapital = tracker.getTotalCapitalInvestedSoFar();
                Double dsipTotalNetProfitPercentage = financialCalculator.calculateProfitPercentage(
                                dsipTotalMarketValue,
                                dsipTotalInvestedCapital);

                // --- History ---
                List<com.dsip.backend.dto.TrackerDetailsDto.HistoryItem> history = dsipTrackerMapper
                                .findExecutionHistoryByTrackerId(trackerId, 6);

                // --- Live Investment Cycle ---
                // Fetch active partition if exists
                DsipPartition activePartition = dsipTrackerMapper.findActivePartitionByTrackerId(trackerId)
                                .orElse(null);
                com.dsip.backend.dto.TrackerDetailsDto.LiveInvestmentCycle liveCycle = new com.dsip.backend.dto.TrackerDetailsDto.LiveInvestmentCycle(
                                0, 0.0, 0.0);

                if (activePartition != null) {
                        Integer capitalDeployedInCycle = activePartition.getCapitalInvestedSoFar();
                        Double partitionProgress = 0.0;
                        if (activePartition.getPartitionCapitalAllocated() > 0) {
                                partitionProgress = ((double) capitalDeployedInCycle
                                                / activePartition.getPartitionCapitalAllocated())
                                                * 100;
                        }

                        Double cycleNetProfitWrapper = 0.0;
                        if (capitalDeployedInCycle > 0) {
                                double cycleMarketValue = financialCalculator.calculateMarketValue(currentPrice,
                                                activePartition.getNoOfSharesBought());
                                // We use local calculation here or add helper for this specific case if needed,
                                // but calculateProfitPercentage works
                                Double p = financialCalculator.calculateProfitPercentage(cycleMarketValue,
                                                capitalDeployedInCycle);
                                if (p != null)
                                        cycleNetProfitWrapper = p;
                        }

                        liveCycle = new com.dsip.backend.dto.TrackerDetailsDto.LiveInvestmentCycle(
                                        capitalDeployedInCycle,
                                        partitionProgress,
                                        cycleNetProfitWrapper);
                }

                // --- Total Cycles Estimation ---
                // Logic: Find median of completed partition lengths (End Date - Start Date). If
                // none, use tracker.partitionDays.
                // Then TotalCycles = (ConvictionYears * 252) / MedianDays
                Integer totalCycles = 0;
                if (tracker.getConvictionPeriodYears() != null && tracker.getConvictionPeriodYears() > 0) {
                        List<DsipPartition> completedPartitions = dsipTrackerMapper.findCompletedPartitions(trackerId);
                        int medianDays;

                        if (completedPartitions.isEmpty()) {
                                medianDays = tracker.getPartitionDays() != null && tracker.getPartitionDays() > 0
                                                ? tracker.getPartitionDays()
                                                : 20; // Default fallback
                        } else {
                                List<Integer> actualLengths = completedPartitions.stream()
                                                .map(p -> financialCalculator.calculateDaysBetween(p.getCreatedAt(),
                                                                p.getPartitionEndDate()))
                                                .filter(days -> days > 0)
                                                .collect(java.util.stream.Collectors.toList());

                                if (actualLengths.isEmpty()) {
                                        medianDays = tracker.getPartitionDays() != null
                                                        && tracker.getPartitionDays() > 0
                                                                        ? tracker.getPartitionDays()
                                                                        : 20;
                                } else {
                                        medianDays = financialCalculator.calculateMedian(actualLengths);
                                }
                        }

                        // Ensure medianDays is at least 1 to avoid division by zero
                        medianDays = Math.max(1, medianDays);

                        int totalTradingDays = tracker.getConvictionPeriodYears()
                                        * financialCalculator.getTradingDaysPerYear();
                        totalCycles = Math.max(1, totalTradingDays / medianDays);
                }

                return com.dsip.backend.dto.TrackerDetailsDto.builder()
                                .id(tracker.getTrackerId())
                                .symbol(tracker.getStockSymbol())
                                .name(tracker.getStockName())
                                .convictionPeriodYears(tracker.getConvictionPeriodYears())
                                .totalCapitalPlanned(tracker.getTotalCapitalPlanned())
                                .partitionDays(tracker.getPartitionDays())
                                .deploymentStyle(com.dsip.backend.enums.DeploymentStyle
                                                .fromValue(tracker.getDeploymentStyle()).name())
                                .baseConvictionScore(tracker.getBaseConvictionScore())
                                .status(tracker.getStatus())

                                .totalCapitalInvestedSoFar((double) totalInvested)
                                .currentTotalValue(currentTotalValue)
                                .netProfitPercentage(netProfitPercentage)

                                .dsipTotalMarketValue(dsipTotalMarketValue)
                                .dsipTotalInvestedCapitalSoFar((double) dsipTotalInvestedCapital)
                                .dsipNetProfitPercentage(dsipTotalNetProfitPercentage)

                                .activePartitionIndex(tracker.getActivePartitionIndex())
                                .totalCycles(totalCycles)

                                .history(history)
                                .liveInvestmentCycle(liveCycle)
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
                if (dto.getTotalCapitalPlanned() != null) {
                        if (dto.getTotalCapitalPlanned() < tracker.getTotalCapitalPlanned()) {
                                throw new com.dsip.backend.exception.InvalidCapitalUpdateException(
                                                tracker.getTotalCapitalPlanned(),
                                                dto.getTotalCapitalPlanned());
                        }
                        tracker.setTotalCapitalPlanned(dto.getTotalCapitalPlanned());
                }
                if (dto.getConvictionPeriodYears() != null) {
                        if (dto.getConvictionPeriodYears() < tracker.getConvictionPeriodYears()) {
                                throw new com.dsip.backend.exception.InvalidTrackerUpdateException(
                                                "Conviction period years",
                                                tracker.getConvictionPeriodYears(),
                                                dto.getConvictionPeriodYears());
                        }
                        tracker.setConvictionPeriodYears(dto.getConvictionPeriodYears());
                }
                if (dto.getPartitionDays() != null) {
                        if (dto.getPartitionDays() < tracker.getPartitionDays()) {
                                throw new com.dsip.backend.exception.InvalidTrackerUpdateException(
                                                "Partition days",
                                                tracker.getPartitionDays(),
                                                dto.getPartitionDays());
                        }
                        tracker.setPartitionDays(dto.getPartitionDays());
                }

                dsipTrackerMapper.updateTracker(tracker);
        }

        @org.springframework.transaction.annotation.Transactional
        public void deleteTracker(Integer trackerId, UUID userId) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new UnauthorizedTrackerAccessException(trackerId, userId);
                }

                // Delete associated data first due to foreign key constraints
                dsipTrackerMapper.deleteExecutionsByTrackerId(trackerId);
                dsipTrackerMapper.deletePartitionsByTrackerId(trackerId);
                dsipTrackerMapper.deleteTrackerById(trackerId);
        }

        public List<com.dsip.backend.dto.DsipExecutionDto> getRecentExecutions(Integer trackerId, UUID userId,
                        Integer limit) {
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

        public com.dsip.backend.dto.PartitionDetailsDto getPartitionDetails(Integer trackerId, Integer partitionIndex,
                        UUID userId) {
                DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

                if (!tracker.getUserId().equals(userId)) {
                        throw new UnauthorizedTrackerAccessException(trackerId, userId);
                }

                DsipPartition partition = dsipTrackerMapper.findPartitionByTrackerIdAndIndex(trackerId, partitionIndex)
                                .orElseThrow(() -> new PartitionNotFoundException(trackerId));

                Double netProfitPercentage = 0.0;
                Double currentMarketValue = 0.0;
                int sharesBought = partition.getNoOfSharesBought() != null ? partition.getNoOfSharesBought() : 0;
                int capitalInvested = partition.getCapitalInvestedSoFar() != null ? partition.getCapitalInvestedSoFar() : 0;

                if (capitalInvested > 0) {
                        com.dsip.backend.entity.Stock stock = stockMapper.findById(Long.valueOf(tracker.getStockId()))
                                        .orElseThrow(() -> new StockNotFoundException(
                                                        String.valueOf(tracker.getStockId())));

                        currentMarketValue = financialCalculator.calculateMarketValue(
                                        stock.getLastDateMarketClosingPrice(), sharesBought);
                        netProfitPercentage = financialCalculator.calculateProfitPercentage(currentMarketValue,
                                        capitalInvested);
                }

                return com.dsip.backend.dto.PartitionDetailsDto.builder()
                                .partitionIndex(partition.getPartitionIndex())
                                .status(partition.getStatus())
                                .capitalAllocated(partition.getPartitionCapitalAllocated())
                                .capitalDeployed((double) capitalInvested)
                                .sharesBought(sharesBought)
                                .currentMarketValue(currentMarketValue)
                                .netProfitPercentage(netProfitPercentage)
                                .growthCount(partition.getSuccessfulGrowthCount() != null ? partition.getSuccessfulGrowthCount() : 0)
                                .expectedDays(partition.getExpectedPartitionDays())
                                .startDate(partition.getCreatedAt())
                                .endDate(partition.getPartitionEndDate())
                                .build();
        }

        private DsipTrackerDto toTrackerDto(DsipTracker tracker) {
                String stockSymbol = stockMapper.findById(tracker.getStockId().longValue())
                                .map(Stock::getStockSymbol)
                                .orElse(null);
                return DsipTrackerDto.fromEntity(tracker, stockSymbol);
        }
}
