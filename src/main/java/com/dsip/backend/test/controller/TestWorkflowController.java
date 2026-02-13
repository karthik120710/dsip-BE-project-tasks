package com.dsip.backend.test.controller;

import com.dsip.backend.auth.CurrentUser;
import com.dsip.backend.entity.User;
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
     * 1. Creates a new DSIP tracker with the provided configuration
     * 2. Loops through each day in the CSV
     * 3. Calls the recommendation API to get the recommended amount
     * 4. Records the execution using the expected price from the CSV
     * 5. Auto-creates new partitions when partitions end
     * 6. Exports an execution log CSV with all daily results
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
        log.info("Executing workflow for symbol: {} using CSV: {} (user: {})",
                request.getStockSymbol(), request.getCsvFilePath(),
                user != null ? user.getEmail() : "test-user");

        try {
            ExecuteWorkflowResponse response = testWorkflowService.executeWorkflow(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute workflow for {}: {}", request.getStockSymbol(), e.getMessage(), e);
            return ResponseEntity.ok(ExecuteWorkflowResponse.error(e.getMessage()));
        }
    }
}
