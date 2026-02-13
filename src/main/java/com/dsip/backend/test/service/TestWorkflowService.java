package com.dsip.backend.test.service;

import com.dsip.backend.data.DataGeneratorProperties;
import com.dsip.backend.data.HistoricalDataService;
import com.dsip.backend.dto.DsipExecutionRequestDto;
import com.dsip.backend.dto.ExecutionResponseDto;
import com.dsip.backend.dto.RecommendationResponseDto;
import com.dsip.backend.dto.TrackerDetailsDto;
import com.dsip.backend.entity.User;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.mapper.UserMapper;
import com.dsip.backend.service.DsipTrackerService;
import com.dsip.backend.service.ExecutionService;
import com.dsip.backend.service.RecommendationService;
import com.dsip.backend.simulation.OhlcData;
import com.dsip.backend.test.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.UUID;

/**
 * Service for test workflow operations.
 * Generates price data CSV and executes the full DSIP workflow using real APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestWorkflowService {

    private final HistoricalDataService historicalDataService;
    private final DataGeneratorProperties dataGeneratorProperties;
    private final DsipTrackerService dsipTrackerService;
    private final RecommendationService recommendationService;
    private final ExecutionService executionService;
    private final UserMapper userMapper;

    private static final String TEST_USER_EMAIL = "test-workflow@dsip.local";

    private static final String TEST_DATA_SUBDIR = "test";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Generate price data CSV with lock-in percentages and expected prices.
     */
    public GeneratePriceDataResponse generatePriceDataCsv(GeneratePriceDataRequest request) throws Exception {
        String symbol = request.getSymbol().toUpperCase();
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        int defaultConviction = request.getDefaultConvictionScore() != null ? request.getDefaultConvictionScore() : 9;

        log.info("Generating test price data CSV for {} from {} to {}", symbol, startDate, endDate);

        // Fetch historical data from Yahoo Finance
        List<OhlcData> ohlcData = historicalDataService.fetchHistoricalData(symbol, startDate, endDate);

        if (ohlcData.isEmpty()) {
            return GeneratePriceDataResponse.error(symbol, "No data found for the specified date range");
        }

        // Convert to test price data with lock-in % and expected price
        List<TestPriceData> priceDataList = convertToTestPriceData(ohlcData, defaultConviction);

        // Write to CSV
        Path csvPath = writePriceDataCsv(symbol, priceDataList, startDate, endDate);

        // Build response
        return buildGenerateResponse(symbol, priceDataList, csvPath);
    }

    /**
     * Execute the full DSIP workflow using the price data CSV.
     */
    public ExecuteWorkflowResponse executeWorkflow(ExecuteWorkflowRequest request, UUID userId) throws Exception {
        String csvFilePath = request.getCsvFilePath();
        Integer trackerId = request.getTrackerId();

        // Use test user if no authenticated user
        if (userId == null) {
            userId = getOrCreateTestUser();
        }

        // Fetch existing tracker details
        TrackerDetailsDto trackerDetails = dsipTrackerService.getTrackerDetailsDto(trackerId, userId);
        String stockSymbol = trackerDetails.getSymbol();
        int convictionScore = trackerDetails.getBaseConvictionScore();

        log.info("Executing test workflow for tracker {} ({}) using CSV: {}", trackerId, stockSymbol, csvFilePath);

        // Load price data from CSV
        List<TestPriceData> priceDataList = loadPriceDataFromCsv(csvFilePath);

        if (priceDataList.isEmpty()) {
            return ExecuteWorkflowResponse.error("No data found in CSV file");
        }

        // Execute workflow day by day
        List<ExecutionLogEntry> executionLog = new ArrayList<>();
        int partitionsCreated = 0;
        double totalCapitalInvested = 0.0;
        double totalSharesAcquired = 0.0;
        int currentPartitionIndex = trackerDetails.getActivePartitionIndex();

        for (int i = 0; i < priceDataList.size(); i++) {
            TestPriceData dayData = priceDataList.get(i);

            try {
                // Get recommendation
                RecommendationResponseDto recommendation = recommendationService.getRecommendation(
                        trackerId, userId, dayData.getLockInPct());

                double recommendedAmount = recommendation.getRecommendedAmount();
                dayData.setExecutedAmount(recommendedAmount);

                // Skip if no recommendation (partition not active)
                if (recommendedAmount <= 0) {
                    log.debug("Day {}: No recommendation (partition not active)", dayData.getDate());

                    // Log skipped day
                    ExecutionLogEntry logEntry = ExecutionLogEntry.builder()
                            .date(dayData.getDate())
                            .partitionIndex(currentPartitionIndex)
                            .lockInPct(dayData.getLockInPct())
                            .convictionScore(convictionScore)
                            .executedPrice(dayData.getExecutedPrice())
                            .recommendedAmount(0.0)
                            .sharesAcquired(0.0)
                            .totalShares(totalSharesAcquired)
                            .portfolioValue(totalSharesAcquired * dayData.getClose())
                            .partitionStatus("SKIPPED")
                            .build();
                    executionLog.add(logEntry);
                    continue;
                }

                // Execute trade
                DsipExecutionRequestDto executionRequest = DsipExecutionRequestDto.builder()
                        .lockInPercentage(dayData.getLockInPct())
                        .convictionOverride(convictionScore)
                        .executedAmount(recommendedAmount)
                        .executionPrice(dayData.getExecutedPrice())
                        .build();

                ExecutionResponseDto executionResponse = executionService.executeTrade(trackerId, userId, executionRequest);

                // Calculate shares acquired
                double sharesAcquired = recommendedAmount / dayData.getExecutedPrice();
                totalCapitalInvested += recommendedAmount;
                totalSharesAcquired += sharesAcquired;

                // Check if partition ended
                String partitionStatus = "ACTIVE";
                if (executionResponse.getEndReason() != null) {
                    partitionStatus = executionResponse.getEndReason().name();

                    // Check if a new partition was created
                    if (executionResponse.getEndReason() == EndReason.SUCCESS ||
                        executionResponse.getEndReason() == EndReason.KILL_SWITCH ||
                        executionResponse.getEndReason() == EndReason.NEUTRAL_PARTITION) {

                        // Call end-action for KILL_SWITCH and NEUTRAL_PARTITION (mirrors production client flow)
                        if (executionResponse.getEndReason() == EndReason.KILL_SWITCH ||
                            executionResponse.getEndReason() == EndReason.NEUTRAL_PARTITION) {
                            log.info("Calling end-action for partition {} ({})",
                                    currentPartitionIndex, executionResponse.getEndReason());
                            dsipTrackerService.handlePartitionEndAction(trackerId, currentPartitionIndex, userId);
                        }

                        // New partition may have been created - update index
                        currentPartitionIndex++;
                        partitionsCreated++;
                        log.info("Partition ended with {}. New partition index: {}",
                                executionResponse.getEndReason(), currentPartitionIndex);
                    }
                }

                // Log entry
                ExecutionLogEntry logEntry = ExecutionLogEntry.builder()
                        .date(dayData.getDate())
                        .partitionIndex(currentPartitionIndex)
                        .lockInPct(dayData.getLockInPct())
                        .convictionScore(convictionScore)
                        .executedPrice(dayData.getExecutedPrice())
                        .recommendedAmount(recommendedAmount)
                        .sharesAcquired(sharesAcquired)
                        .totalShares(totalSharesAcquired)
                        .portfolioValue(totalSharesAcquired * dayData.getClose())
                        .partitionStatus(partitionStatus)
                        .build();
                executionLog.add(logEntry);

                log.debug("Day {}: Invested ${}, Shares: {}, Total: {}",
                        dayData.getDate(), recommendedAmount, sharesAcquired, totalSharesAcquired);

            } catch (Exception e) {
                log.error("Error processing day {}: {}", dayData.getDate(), e.getMessage());

                // Log error entry
                ExecutionLogEntry logEntry = ExecutionLogEntry.builder()
                        .date(dayData.getDate())
                        .partitionIndex(currentPartitionIndex)
                        .lockInPct(dayData.getLockInPct())
                        .convictionScore(convictionScore)
                        .executedPrice(dayData.getExecutedPrice())
                        .recommendedAmount(0.0)
                        .sharesAcquired(0.0)
                        .totalShares(totalSharesAcquired)
                        .portfolioValue(totalSharesAcquired * dayData.getClose())
                        .partitionStatus("ERROR: " + e.getMessage())
                        .build();
                executionLog.add(logEntry);
            }
        }

        // Rewrite original CSV with executed_amount values populated
        rewritePriceDataCsv(Paths.get(csvFilePath), priceDataList);

        // Calculate final metrics
        double finalPrice = priceDataList.get(priceDataList.size() - 1).getClose();
        double finalPortfolioValue = totalSharesAcquired * finalPrice;
        double overallReturnPct = totalCapitalInvested > 0
                ? ((finalPortfolioValue - totalCapitalInvested) / totalCapitalInvested) * 100
                : 0.0;

        // Write execution log CSV
        Path executionLogPath = writeExecutionLogCsv(stockSymbol, executionLog);

        log.info("Workflow completed: {} days processed, {} partitions, {:.2f}% return",
                priceDataList.size(), partitionsCreated, overallReturnPct);

        // Build response
        return ExecuteWorkflowResponse.builder()
                .success(true)
                .trackerId(trackerId)
                .stockSymbol(stockSymbol)
                .summary(ExecuteWorkflowResponse.Summary.builder()
                        .daysProcessed(priceDataList.size())
                        .partitionsCreated(partitionsCreated)
                        .totalCapitalInvested(round(totalCapitalInvested, 2))
                        .totalSharesAcquired(round(totalSharesAcquired, 4))
                        .finalPortfolioValue(round(finalPortfolioValue, 2))
                        .overallReturnPct(round(overallReturnPct, 2))
                        .build())
                .executionLog(ExecuteWorkflowResponse.ExecutionLog.builder()
                        .filePath(executionLogPath.toAbsolutePath().toString())
                        .fileName(executionLogPath.getFileName().toString())
                        .build())
                .build();
    }

    // ==================== Internal Methods ====================

    /**
     * Convert OHLC data to TestPriceData with lock-in % and expected price.
     */
    private List<TestPriceData> convertToTestPriceData(List<OhlcData> ohlcData, int defaultConviction) {
        List<TestPriceData> result = new ArrayList<>();

        for (int i = 0; i < ohlcData.size(); i++) {
            OhlcData day = ohlcData.get(i);
            double prevClose = i > 0 ? ohlcData.get(i - 1).getClose() : day.getOpen();

            // Determine if red day (open < prev_close)
            boolean isRedDay = day.getOpen() < prevClose;

            // Executed price: Red day = Open, Green day = Close
            double executedPrice = isRedDay ? day.getOpen() : day.getClose();

            // Lock-in %: (executed_price - prev_close) / prev_close * 100
            // Day 1 = 0
            double lockInPct = i == 0 ? 0.0 : ((executedPrice - prevClose) / prevClose) * 100;

            TestPriceData priceData = TestPriceData.builder()
                    .date(day.getDate())
                    .open(day.getOpen())
                    .high(day.getHigh())
                    .low(day.getLow())
                    .close(day.getClose())
                    .prevClose(prevClose)
                    .lockInPct(round(lockInPct, 4))
                    .executedPrice(round(executedPrice, 2))
                    .convictionScore(defaultConviction)
                    .build();

            result.add(priceData);
        }

        return result;
    }

    /**
     * Write price data to CSV file.
     */
    private Path writePriceDataCsv(String symbol, List<TestPriceData> data,
                                    LocalDate startDate, LocalDate endDate) throws IOException {
        Path outputDir = Paths.get(dataGeneratorProperties.getOutputDir(), TEST_DATA_SUBDIR);
        Files.createDirectories(outputDir);

        String fileName = String.format("%s_test_prices_%s_%s.csv",
                symbol, startDate, endDate);
        Path csvPath = outputDir.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            // Header
            writer.write("date,open,high,low,close,prev_close,lock_in_pct,executed_price,conviction_score,executed_amount");
            writer.newLine();

            // Data rows
            for (TestPriceData day : data) {
                writer.write(String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.2f,%d,%.2f",
                        day.getDate(),
                        day.getOpen(),
                        day.getHigh(),
                        day.getLow(),
                        day.getClose(),
                        day.getPrevClose(),
                        day.getLockInPct(),
                        day.getExecutedPrice(),
                        day.getConvictionScore(),
                        day.getExecutedAmount()));
                writer.newLine();
            }
        }

        log.info("Wrote price data CSV to: {}", csvPath.toAbsolutePath());
        return csvPath;
    }

    /**
     * Load price data from CSV file.
     */
    private List<TestPriceData> loadPriceDataFromCsv(String filePath) throws IOException {
        List<TestPriceData> result = new ArrayList<>();
        Path csvPath = Paths.get(filePath);

        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line = reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 9) {
                    TestPriceData priceData = TestPriceData.builder()
                            .date(LocalDate.parse(parts[0]))
                            .open(Double.parseDouble(parts[1]))
                            .high(Double.parseDouble(parts[2]))
                            .low(Double.parseDouble(parts[3]))
                            .close(Double.parseDouble(parts[4]))
                            .prevClose(Double.parseDouble(parts[5]))
                            .lockInPct(Double.parseDouble(parts[6]))
                            .executedPrice(Double.parseDouble(parts[7]))
                            .convictionScore(Integer.parseInt(parts[8]))
                            .executedAmount(parts.length >= 10 ? Double.parseDouble(parts[9]) : 0.0)
                            .build();
                    result.add(priceData);
                }
            }
        }

        return result;
    }

    /**
     * Write execution log to CSV file.
     */
    private Path writeExecutionLogCsv(String symbol, List<ExecutionLogEntry> log) throws IOException {
        Path outputDir = Paths.get(dataGeneratorProperties.getOutputDir(), TEST_DATA_SUBDIR);
        Files.createDirectories(outputDir);

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileName = String.format("%s_execution_log_%s.csv", symbol, timestamp);
        Path csvPath = outputDir.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            // Header
            writer.write("date,partition_index,lock_in_pct,conviction_score,executed_price,recommended_amount,shares_acquired,total_shares,portfolio_value,partition_status");
            writer.newLine();

            // Data rows
            for (ExecutionLogEntry entry : log) {
                writer.write(String.format("%s,%d,%.4f,%d,%.2f,%.2f,%.4f,%.4f,%.2f,%s",
                        entry.getDate(),
                        entry.getPartitionIndex(),
                        entry.getLockInPct(),
                        entry.getConvictionScore(),
                        entry.getExecutedPrice(),
                        entry.getRecommendedAmount(),
                        entry.getSharesAcquired(),
                        entry.getTotalShares(),
                        entry.getPortfolioValue(),
                        entry.getPartitionStatus()));
                writer.newLine();
            }
        }

        return csvPath;
    }

    /**
     * Rewrite price data CSV with enriched data (executed_amount values populated).
     */
    private void rewritePriceDataCsv(Path csvPath, List<TestPriceData> data) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            // Header
            writer.write("date,open,high,low,close,prev_close,lock_in_pct,executed_price,conviction_score,executed_amount");
            writer.newLine();

            // Data rows
            for (TestPriceData day : data) {
                writer.write(String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.2f,%d,%.2f",
                        day.getDate(),
                        day.getOpen(),
                        day.getHigh(),
                        day.getLow(),
                        day.getClose(),
                        day.getPrevClose(),
                        day.getLockInPct(),
                        day.getExecutedPrice(),
                        day.getConvictionScore(),
                        day.getExecutedAmount()));
                writer.newLine();
            }
        }

        log.info("Rewrote price data CSV with executed_amount values: {}", csvPath.toAbsolutePath());
    }

    /**
     * Build the generate response.
     */
    private GeneratePriceDataResponse buildGenerateResponse(String symbol, List<TestPriceData> data, Path csvPath) {
        DoubleSummaryStatistics highStats = data.stream()
                .mapToDouble(TestPriceData::getHigh)
                .summaryStatistics();
        DoubleSummaryStatistics lowStats = data.stream()
                .mapToDouble(TestPriceData::getLow)
                .summaryStatistics();

        double startPrice = data.get(0).getClose();
        double endPrice = data.get(data.size() - 1).getClose();
        double priceChangePct = ((endPrice - startPrice) / startPrice) * 100;

        return GeneratePriceDataResponse.builder()
                .success(true)
                .symbol(symbol)
                .filePath(csvPath.toAbsolutePath().toString())
                .fileName(csvPath.getFileName().toString())
                .recordCount(data.size())
                .dateRange(GeneratePriceDataResponse.DateRange.builder()
                        .start(data.get(0).getDate())
                        .end(data.get(data.size() - 1).getDate())
                        .tradingDays(data.size())
                        .build())
                .priceSummary(GeneratePriceDataResponse.PriceSummary.builder()
                        .startPrice(round(startPrice, 2))
                        .endPrice(round(endPrice, 2))
                        .highPrice(round(highStats.getMax(), 2))
                        .lowPrice(round(lowStats.getMin(), 2))
                        .priceChangePct(round(priceChangePct, 2))
                        .build())
                .build();
    }

    private double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    /**
     * Get or create a test user for workflow execution.
     */
    private UUID getOrCreateTestUser() {
        // Try to find existing test user
        User existingUser = userMapper.findByEmail(TEST_USER_EMAIL).orElse(null);
        if (existingUser != null) {
            return existingUser.getId();
        }

        // Create new test user
        User testUser = User.builder()
                .email(TEST_USER_EMAIL)
                .name("Test Workflow User")
                .build();
        userMapper.insert(testUser);
        log.info("Created test user with ID: {}", testUser.getId());
        return testUser.getId();
    }

    /**
     * Internal class for execution log entries.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ExecutionLogEntry {
        private LocalDate date;
        private int partitionIndex;
        private double lockInPct;
        private int convictionScore;
        private double executedPrice;
        private double recommendedAmount;
        private double sharesAcquired;
        private double totalShares;
        private double portfolioValue;
        private String partitionStatus;
    }
}
