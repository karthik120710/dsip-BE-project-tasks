package com.dsip.backend.test.controller;

import com.dsip.backend.auth.CurrentUser;
import com.dsip.backend.constants.DsipConstants;
import com.dsip.backend.data.HistoricalDataService;
import com.dsip.backend.dto.DsipTrackerDto;
import com.dsip.backend.entity.Exchange;
import com.dsip.backend.entity.User;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.mapper.StockMapper;
import com.dsip.backend.mapper.UserMapper;
import com.dsip.backend.service.DsipTrackerService;
import com.dsip.backend.service.StockService;
import com.dsip.backend.simulation.*;
import com.dsip.backend.test.dto.ExecuteWorkflowRequest;
import com.dsip.backend.test.dto.ExecuteWorkflowResponse;
import com.dsip.backend.test.dto.GeneratePriceDataRequest;
import com.dsip.backend.test.dto.GeneratePriceDataResponse;
import com.dsip.backend.test.service.TestWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Controller for test workflow operations.
 * Only available in dev/test/local profiles.
 *
 * This controller provides endpoints to:
 * 1. Generate price data CSV with lock-in percentages and expected prices
 * 2. Execute the full DSIP workflow using real APIs
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
public class TestWorkflowController {

    private final TestWorkflowService testWorkflowService;
    private final BatchSimulationConfigLoader batchConfigLoader;
    private final HistoricalDataService historicalDataService;
    private final DsipTrackerService dsipTrackerService;
    private final UserMapper userMapper;
    private final StockMapper stockMapper;
    private final StockService stockService;

    private static final String TEST_USER_EMAIL = "test-workflow@dsip.local";

    /**
     * Generate price data CSV with lock-in percentages and expected prices.
     *
     * This endpoint fetches historical OHLC data from Yahoo Finance and calculates:
     * - prev_close: Previous day's closing price
     * - lock_in_pct: (executed_price - prev_close) / prev_close * 100
     * - executed_price: Open price for red days, Close price for green days
     * - conviction_score: Default value (can be modified in CSV)
     *
     * Red day = open < prev_close
     * Green day = open >= prev_close
     *
     * @param request Contains symbol, start_date, end_date, and optional default_conviction_score
     * @return Response with CSV file path and summary statistics
     */
    @PostMapping("/generate-price-data")
    public ResponseEntity<GeneratePriceDataResponse> generatePriceData(
            @Valid @RequestBody GeneratePriceDataRequest request) {

        log.info("Generating price data CSV for symbol: {}", request.getSymbol());

        try {
            GeneratePriceDataResponse response = testWorkflowService.generatePriceDataCsv(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate price data for {}: {}", request.getSymbol(), e.getMessage(), e);
            return ResponseEntity.ok(GeneratePriceDataResponse.error(request.getSymbol(), e.getMessage()));
        }
    }

    /**
     * Execute the full DSIP workflow using the generated price data CSV.
     *
     * This endpoint:
     * 1. Loops through each day in the CSV
     * 2. Calculates the recommended amount before each execution using current partition state
     * 3. Records the execution using the expected price from the CSV
     * 4. Auto-creates new partitions when partitions end
     * 5. Exports an execution log CSV with all daily results
     *
     * If authenticated (via session cookie), uses the authenticated user.
     * If not authenticated, creates/uses a test user.
     *
     * @param request Contains CSV file path and tracker configuration
     * @param user Current authenticated user (null if not authenticated)
     * @return Response with summary and execution log CSV path
     */
    @PostMapping("/execute-workflow")
    public ResponseEntity<ExecuteWorkflowResponse> executeWorkflow(
            @Valid @RequestBody ExecuteWorkflowRequest request,
            @CurrentUser User user) {

        UUID userId = user != null ? user.getId() : null;
        log.info("Executing workflow for tracker: {} using CSV: {} (user: {})",
                request.getTrackerId(), request.getCsvFilePath(),
                user != null ? user.getEmail() : "test-user");

        try {
            ExecuteWorkflowResponse response = testWorkflowService.executeWorkflow(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute workflow for tracker {}: {}", request.getTrackerId(), e.getMessage(), e);
            return ResponseEntity.ok(ExecuteWorkflowResponse.error(e.getMessage()));
        }
    }

    /**
     * Batch simulate multiple stocks from a config CSV file.
     * Processes each stock sequentially — fetches OHLC data, runs simulation, exports results.
     * Individual stock failures are captured and do not stop the batch.
     *
     * @param request batch simulation request with CSV config path
     * @return batch result with per-stock summaries
     */
    @PostMapping("/batch-simulate")
    public ResponseEntity<BatchSimulationResult> batchSimulate(
            @Valid @RequestBody BatchSimulationRequest request,
            @CurrentUser User user) {

        log.info("Starting batch simulation from config: {} (user: {})",
                request.getCsvConfigPath(),
                user != null ? user.getEmail() : "test-user");

        List<BatchSimulationConfigEntry> entries;
        try {
            entries = batchConfigLoader.load(request.getCsvConfigPath());
        } catch (Exception e) {
            log.error("Failed to load batch config CSV: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(BatchSimulationResult.builder()
                    .totalStocks(0)
                    .successCount(0)
                    .failureCount(0)
                    .results(List.of())
                    .build());
        }

        UUID userId = user != null ? user.getId() : getOrCreateTestUser();

        List<BatchSimulationResult.StockSimulationSummary> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < entries.size(); i++) {
            BatchSimulationConfigEntry entry = entries.get(i);
            String symbol = entry.getTickerSymbol();
            log.info("[{}/{}] Processing {} ({} to {})",
                    i + 1, entries.size(), symbol, entry.getStartDate(), entry.getEndDate());

            try {
                BatchSimulationResult.StockSimulationSummary summary = processStock(entry, userId);
                results.add(summary);
                successCount++;
                log.info("[{}/{}] {} completed: trackerId={}, {}% return, {} partitions, {} days",
                        i + 1, entries.size(), symbol,
                        summary.getTrackerId(),
                        String.format("%.2f", summary.getOverallReturnPct()),
                        summary.getPartitionsCreated(),
                        summary.getDaysSimulated());
            } catch (Exception e) {
                log.error("[{}/{}] {} failed: {}", i + 1, entries.size(), symbol, e.getMessage(), e);
                results.add(BatchSimulationResult.StockSimulationSummary.builder()
                        .symbol(symbol)
                        .success(false)
                        .error(e.getMessage())
                        .build());
                failureCount++;
            }
        }

        log.info("Batch simulation complete: {}/{} succeeded, {} failed",
                successCount, entries.size(), failureCount);

        return ResponseEntity.ok(BatchSimulationResult.builder()
                .totalStocks(entries.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .results(results)
                .build());
    }

    // ==================== Batch Simulation Internals ====================

    private BatchSimulationResult.StockSimulationSummary processStock(
            BatchSimulationConfigEntry entry,
            UUID userId) throws Exception {

        String symbol = entry.getTickerSymbol();

        // Step 1: Ensure stock exists in DB
        if (!stockMapper.existsByStockSymbol(symbol)) {
            log.info("Stock {} not found in DB, calling Stock API to populate...", symbol);
            stockService.getClosingPrice(symbol, Exchange.US);
            log.info("Stock {} populated in DB via Stock API.", symbol);
        } else {
            log.info("Stock {} already exists in DB.", symbol);
        }

        // Step 2: Fetch historical OHLC data (full date range)
        List<OhlcData> fullOhlcData = historicalDataService.fetchHistoricalData(
                symbol, entry.getStartDate(), entry.getEndDate());
        if (fullOhlcData.isEmpty()) {
            throw new RuntimeException("No historical data found for " + symbol
                    + " between " + entry.getStartDate() + " and " + entry.getEndDate());
        }

        // Step 3: Trim OHLC data to conviction period trading days
        int daysToSimulate = (int) Math.round(entry.getConvictionPeriodYears() * 252);
        List<OhlcData> trimmedOhlcData = fullOhlcData.size() > daysToSimulate
                ? fullOhlcData.subList(0, daysToSimulate)
                : fullOhlcData;
        log.info("Stock {} conviction period {} yrs → {} trading days, available {} days, simulating {} days",
                symbol, entry.getConvictionPeriodYears(), daysToSimulate,
                fullOhlcData.size(), trimmedOhlcData.size());

        // Step 4: Generate price data CSV using trimmed date range
        LocalDate trimmedStart = trimmedOhlcData.get(0).getDate();
        LocalDate trimmedEnd = trimmedOhlcData.get(trimmedOhlcData.size() - 1).getDate();
        GeneratePriceDataRequest priceDataRequest = GeneratePriceDataRequest.builder()
                .symbol(symbol)
                .startDate(trimmedStart)
                .endDate(trimmedEnd)
                .defaultConvictionScore(entry.getConvictionScore())
                .build();
        GeneratePriceDataResponse priceDataResponse = testWorkflowService.generatePriceDataCsv(priceDataRequest);
        if (!priceDataResponse.isSuccess()) {
            throw new RuntimeException("Failed to generate price data CSV for " + symbol
                    + ": " + priceDataResponse.getError());
        }
        String csvFilePath = priceDataResponse.getFilePath();
        log.info("Stock {} price data CSV generated: {}", symbol, csvFilePath);

        // Step 5: Create tracker
        DsipTrackerDto trackerDto = DsipTrackerDto.builder()
                .stockSymbol(symbol)
                .convictionPeriodYears(entry.getConvictionPeriodYears())
                .totalCapitalPlanned(entry.getTotalCapital())
                .partitionDays(entry.getExpectedPartitionDays())
                .partitionMonths(entry.getExpectedPartitionDays() / DsipConstants.TRADING_DAYS_PER_MONTH)
                .deploymentStyle(DeploymentStyle.fromKey(entry.getDeploymentStyle()))
                .baseConvictionScore(entry.getConvictionScore())
                .initialInvestedAmount(entry.getInitialInvestedAmount())
                .initialSharesHeld(entry.getInitialSharesHeld())
                .isFractionalSharesAllowed(true)
                .build();
        DsipTrackerDto createdTracker = dsipTrackerService.createTracker(userId, trackerDto);
        Integer trackerId = createdTracker.getTrackerId();
        log.info("Stock {} tracker created with ID: {}", symbol, trackerId);

        // Step 6: Execute workflow
        ExecuteWorkflowRequest workflowRequest = ExecuteWorkflowRequest.builder()
                .csvFilePath(csvFilePath)
                .trackerId(trackerId)
                .build();
        ExecuteWorkflowResponse workflowResponse = testWorkflowService.executeWorkflow(workflowRequest, userId);
        if (!workflowResponse.isSuccess()) {
            throw new RuntimeException("Workflow execution failed for " + symbol
                    + ": " + workflowResponse.getError());
        }
        log.info("Stock {} workflow executed successfully", symbol);

        // Step 7: Build result
        ExecuteWorkflowResponse.Summary summary = workflowResponse.getSummary();
        String executionLogCsvPath = workflowResponse.getExecutionLog() != null
                ? workflowResponse.getExecutionLog().getFilePath()
                : null;

        return BatchSimulationResult.StockSimulationSummary.builder()
                .symbol(symbol)
                .success(true)
                .trackerId(trackerId)
                .executionLogCsvPath(executionLogCsvPath)
                .daysSimulated(summary.getDaysProcessed())
                .partitionsCreated(summary.getPartitionsCreated())
                .overallReturnPct(summary.getOverallReturnPct())
                .totalCapitalInvested(summary.getTotalCapitalInvested())
                .finalPortfolioValue(summary.getFinalPortfolioValue())
                .build();
    }

    private UUID getOrCreateTestUser() {
        User existingUser = userMapper.findByEmail(TEST_USER_EMAIL).orElse(null);
        if (existingUser != null) {
            return existingUser.getId();
        }
        User testUser = User.builder()
                .email(TEST_USER_EMAIL)
                .name("Batch Simulation User")
                .build();
        userMapper.insert(testUser);
        log.info("Created test user with ID: {}", testUser.getId());
        return testUser.getId();
    }
}
