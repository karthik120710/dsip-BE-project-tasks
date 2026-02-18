package com.dsip.backend.data;

import com.dsip.backend.data.dto.DataGeneratorRequest;
import com.dsip.backend.data.dto.DataGeneratorResponse;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.simulation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;

/**
 * Controller for historical data generation and simulation.
 * Only available in dev/test/local profiles.
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
public class DataGeneratorController {

    private final HistoricalDataService historicalDataService;
    private final CsvExportService csvExportService;
    private final SimulationService simulationService;
    private final SimulationCsvExporter simulationCsvExporter;
    private final CsvPriceDataLoader csvLoader;

    /**
     * Generate CSV file with historical OHLCV data.
     *
     * @param request Data generation request
     * @return Response with file path and summary
     */
    @PostMapping("/generate")
    public ResponseEntity<DataGeneratorResponse> generateCsv(
            @Valid @RequestBody DataGeneratorRequest request) {

        try {
            return ResponseEntity.ok(generateCsvInternal(request, false));
        } catch (Exception e) {
            log.error("Failed to generate CSV for {}: {}", request.getSymbol(), e.getMessage(), e);
            return ResponseEntity.ok(DataGeneratorResponse.error(request.getSymbol(), e.getMessage()));
        }
    }

    /**
     * Generate CSV and run simulation.
     *
     * @param request Data generation request with simulation config
     * @return Response with file path, summary, and simulation results
     */
    @PostMapping("/generate-and-simulate")
    public ResponseEntity<DataGeneratorResponse> generateAndSimulate(
            @Valid @RequestBody DataGeneratorRequest request) {

        try {
            request.setRunSimulation(true);
            return ResponseEntity.ok(generateCsvInternal(request, true));
        } catch (Exception e) {
            log.error("Failed to generate and simulate for {}: {}", request.getSymbol(), e.getMessage(), e);
            return ResponseEntity.ok(DataGeneratorResponse.error(request.getSymbol(), e.getMessage()));
        }
    }

    /**
     * Validate if a symbol exists.
     *
     * @param symbol Stock/ETF/Crypto symbol
     * @return Validation result
     */
    @GetMapping("/symbols/validate")
    public ResponseEntity<Map<String, Object>> validateSymbol(@RequestParam String symbol) {
        boolean valid = historicalDataService.validateSymbol(symbol);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "valid", valid,
                "message", valid ? "Symbol found" : "Symbol not found on Yahoo Finance"
        ));
    }

    /**
     * List all generated CSV files.
     *
     * @return List of CSV file names
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listCsvFiles() {
        try {
            List<String> files = csvExportService.listCsvFiles();
            return ResponseEntity.ok(Map.of(
                    "outputDirectory", csvExportService.getOutputDirectory(),
                    "files", files,
                    "count", files.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "error", e.getMessage(),
                    "files", List.of(),
                    "count", 0
            ));
        }
    }

    /**
     * Delete a CSV file.
     *
     * @param fileName File name to delete
     * @return Deletion result
     */
    @DeleteMapping("/files/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteCsvFile(@PathVariable String fileName) {
        boolean deleted = csvExportService.deleteCsvFile(fileName);
        return ResponseEntity.ok(Map.of(
                "fileName", fileName,
                "deleted", deleted
        ));
    }

    /**
     * Run simulation on an existing CSV file.
     *
     * @param fileName       CSV file name
     * @param request        Simulation configuration
     * @return Simulation result
     */
    @PostMapping("/simulate/{fileName}")
    public ResponseEntity<SimulationResult> simulateFromFile(
            @PathVariable String fileName,
            @RequestBody SimulationController.SimulationRequest request) {

        try {
            Path csvPath = csvExportService.getCsvPath(fileName);
            List<OhlcData> ohlcData = csvLoader.loadFromFile(csvPath.toString());

            if (ohlcData.isEmpty()) {
                throw new IllegalArgumentException("No data in file: " + fileName);
            }

            SimulationConfig config = buildSimulationConfig(request, ohlcData);
            SimulationResult result = simulationService.runSimulation(config);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to simulate from file {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Simulation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get symbol format guide.
     */
    @GetMapping("/symbols/guide")
    public ResponseEntity<Map<String, Object>> getSymbolGuide() {
        return ResponseEntity.ok(Map.of(
                "formats", Map.of(
                        "Indian NSE", Map.of(
                                "format", "SYMBOL.NS",
                                "examples", List.of("RELIANCE.NS", "TCS.NS", "INFY.NS", "HDFCBANK.NS")
                        ),
                        "Indian BSE", Map.of(
                                "format", "SYMBOL.BO",
                                "examples", List.of("RELIANCE.BO", "TCS.BO")
                        ),
                        "US Stocks", Map.of(
                                "format", "SYMBOL",
                                "examples", List.of("AAPL", "GOOGL", "MSFT", "TSLA")
                        ),
                        "US ETFs", Map.of(
                                "format", "SYMBOL",
                                "examples", List.of("SPY", "QQQ", "VTI", "VOO")
                        ),
                        "Crypto", Map.of(
                                "format", "SYMBOL-USD",
                                "examples", List.of("BTC-USD", "ETH-USD", "SOL-USD")
                        )
                ),
                "periods", List.of("1Y", "2Y", "3Y", "5Y", "10Y", "MAX")
        ));
    }

    /**
     * Fetch data, run simulation, and export detailed results to CSV.
     * This is the complete workflow for testing with historical data.
     *
     * @param request Data generation request with simulation config
     * @return Response with both OHLC data file and simulation CSV file paths
     */
    @PostMapping("/generate-simulate-export")
    public ResponseEntity<Map<String, Object>> generateSimulateAndExport(
            @Valid @RequestBody DataGeneratorRequest request) {

        try {
            String symbol = request.getSymbol().toUpperCase();
            LocalDate startDate = request.getEffectiveStartDate();
            LocalDate endDate = request.getEffectiveEndDate();

            log.info("Full workflow: Generate + Simulate + Export for {} from {} to {}", symbol, startDate, endDate);

            // Step 1: Fetch historical data
            List<OhlcData> ohlcData = historicalDataService.fetchHistoricalData(symbol, startDate, endDate);
            if (ohlcData.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "error", "No data found for the specified date range"
                ));
            }

            // Step 2: Export OHLC data to CSV
            Path ohlcCsvPath = csvExportService.exportToCsv(symbol, ohlcData, startDate, endDate);

            // Step 3: Build simulation config
            SimulationController.SimulationRequest simRequest = request.getSimulationConfig();
            if (simRequest == null) {
                simRequest = new SimulationController.SimulationRequest();
            }
            SimulationConfig config = buildSimulationConfig(simRequest, ohlcData);

            // Step 4: Run simulation
            SimulationResult simulationResult = simulationService.runSimulation(config);

            // Step 5: Export simulation results to detailed CSV
            Path simulationCsvPath = simulationCsvExporter.exportSimulation(simulationResult, symbol);

            log.info("Full workflow completed for {}: OHLC CSV at {}, Simulation CSV at {}",
                    symbol, ohlcCsvPath, simulationCsvPath);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "symbol", symbol,
                    "ohlcData", Map.of(
                            "filePath", ohlcCsvPath.toAbsolutePath().toString(),
                            "fileName", ohlcCsvPath.getFileName().toString(),
                            "recordCount", ohlcData.size(),
                            "dateRange", Map.of(
                                    "start", ohlcData.get(0).getDate().toString(),
                                    "end", ohlcData.get(ohlcData.size() - 1).getDate().toString()
                            )
                    ),
                    "simulation", Map.of(
                            "filePath", simulationCsvPath.toAbsolutePath().toString(),
                            "fileName", simulationCsvPath.getFileName().toString(),
                            "daysSimulated", simulationResult.getTotalDaysSimulated(),
                            "partitionsCreated", simulationResult.getPartitionsCreated(),
                            "overallReturnPct", simulationResult.getOverallReturnPct(),
                            "totalCapitalInvested", simulationResult.getTotalCapitalInvested(),
                            "finalPortfolioValue", simulationResult.getFinalPortfolioValue()
                    )
            ));

        } catch (Exception e) {
            log.error("Failed full workflow for {}: {}", request.getSymbol(), e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Export existing simulation result to detailed CSV.
     *
     * @param fileName OHLC CSV file name to run simulation on
     * @param request  Simulation configuration
     * @return Response with simulation CSV file path
     */
    @PostMapping("/export-simulation/{fileName}")
    public ResponseEntity<Map<String, Object>> exportSimulation(
            @PathVariable String fileName,
            @RequestBody SimulationController.SimulationRequest request) {

        try {
            Path csvPath = csvExportService.getCsvPath(fileName);
            List<OhlcData> ohlcData = csvLoader.loadFromFile(csvPath.toString());

            if (ohlcData.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "error", "No data in file: " + fileName
                ));
            }

            // Extract symbol from filename (format: SYMBOL_STARTDATE_ENDDATE.csv)
            String symbol = fileName.split("_")[0];

            // Build config and run simulation
            SimulationConfig config = buildSimulationConfig(request, ohlcData);
            SimulationResult result = simulationService.runSimulation(config);

            // Export to CSV
            Path simulationCsvPath = simulationCsvExporter.exportSimulation(result, symbol);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "symbol", symbol,
                    "simulationCsv", Map.of(
                            "filePath", simulationCsvPath.toAbsolutePath().toString(),
                            "fileName", simulationCsvPath.getFileName().toString()
                    ),
                    "summary", Map.of(
                            "daysSimulated", result.getTotalDaysSimulated(),
                            "partitionsCreated", result.getPartitionsCreated(),
                            "overallReturnPct", result.getOverallReturnPct(),
                            "totalCapitalInvested", result.getTotalCapitalInvested(),
                            "finalPortfolioValue", result.getFinalPortfolioValue()
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to export simulation for {}: {}", fileName, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== Internal Methods ====================

    private DataGeneratorResponse generateCsvInternal(DataGeneratorRequest request, boolean runSimulation)
            throws Exception {

        String symbol = request.getSymbol().toUpperCase();
        LocalDate startDate = request.getEffectiveStartDate();
        LocalDate endDate = request.getEffectiveEndDate();

        log.info("Generating CSV for {} from {} to {}", symbol, startDate, endDate);

        // Fetch historical data
        List<OhlcData> ohlcData = historicalDataService.fetchHistoricalData(symbol, startDate, endDate);

        if (ohlcData.isEmpty()) {
            return DataGeneratorResponse.error(symbol, "No data found for the specified date range");
        }

        // Export to CSV
        Path csvPath = csvExportService.exportToCsv(symbol, ohlcData, startDate, endDate);

        // Build response
        DataGeneratorResponse.DataGeneratorResponseBuilder responseBuilder = DataGeneratorResponse.builder()
                .success(true)
                .symbol(symbol)
                .filePath(csvPath.toAbsolutePath().toString())
                .fileName(csvPath.getFileName().toString())
                .recordCount(ohlcData.size())
                .dateRange(buildDateRange(ohlcData))
                .priceSummary(buildPriceSummary(ohlcData));

        // Run simulation if requested
        if (runSimulation && request.getSimulationConfig() != null) {
            SimulationConfig config = buildSimulationConfig(request.getSimulationConfig(), ohlcData);
            SimulationResult simulationResult = simulationService.runSimulation(config);
            responseBuilder.simulationResult(simulationResult);
            log.info("Simulation completed: {} return",
                    String.format("%.2f%%", simulationResult.getOverallReturnPct()));
        }

        return responseBuilder.build();
    }

    private DataGeneratorResponse.DateRange buildDateRange(List<OhlcData> data) {
        return DataGeneratorResponse.DateRange.builder()
                .start(data.get(0).getDate())
                .end(data.get(data.size() - 1).getDate())
                .tradingDays(data.size())
                .build();
    }

    private DataGeneratorResponse.PriceSummary buildPriceSummary(List<OhlcData> data) {
        double startPrice = data.get(0).getClose();
        double endPrice = data.get(data.size() - 1).getClose();

        DoubleSummaryStatistics highStats = data.stream()
                .mapToDouble(OhlcData::getHigh)
                .summaryStatistics();

        DoubleSummaryStatistics lowStats = data.stream()
                .mapToDouble(OhlcData::getLow)
                .summaryStatistics();

        double priceChange = ((endPrice - startPrice) / startPrice) * 100;

        return DataGeneratorResponse.PriceSummary.builder()
                .startPrice(startPrice)
                .endPrice(endPrice)
                .highPrice(highStats.getMax())
                .lowPrice(lowStats.getMin())
                .priceChangePct(Math.round(priceChange * 100.0) / 100.0)
                .build();
    }

    private SimulationConfig buildSimulationConfig(SimulationController.SimulationRequest request,
                                                    List<OhlcData> ohlcData) {
        return SimulationConfig.builder()
                .totalCapital(request.getTotalCapital() != null ? request.getTotalCapital() : 100000.0)
                .convictionPeriodYears(request.getConvictionYears() != null ? request.getConvictionYears() : 3.0)
                .stockType(request.getStockType() != null
                        ? StockType.fromKey(request.getStockType())
                        : StockType.MIDCAP)
                .baseConvictionScore(request.getConvictionScore() != null ? request.getConvictionScore() : 80)
                .deploymentStyle(request.getDeploymentStyle() != null ? request.getDeploymentStyle() : "MODERATE")
                .initialPrice(ohlcData.get(0).getClose())
                .maxPartitions(request.getMaxPartitions() != null ? request.getMaxPartitions() : 10)
                .expectedPartitionDays(request.getExpectedDays() != null ? request.getExpectedDays() : 60)
                .scenario(SimulationConfig.PriceScenario.HISTORICAL)
                .historicalData(ohlcData)
                .executionPriceType(request.getExecutionPriceType() != null
                        ? SimulationConfig.ExecutionPriceType.valueOf(request.getExecutionPriceType().toUpperCase())
                        : SimulationConfig.ExecutionPriceType.CLOSE)
                .lockInReferenceType(request.getLockInReferenceType() != null
                        ? SimulationConfig.LockInReferenceType.valueOf(request.getLockInReferenceType().toUpperCase())
                        : SimulationConfig.LockInReferenceType.PREV_CLOSE)
                .build();
    }
}
