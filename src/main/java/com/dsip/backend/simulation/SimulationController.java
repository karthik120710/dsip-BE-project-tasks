package com.dsip.backend.simulation;

import com.dsip.backend.enums.StockType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Controller for running DSIP simulations.
 * Only available in dev/test profiles.
 */
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
public class SimulationController {

    private final SimulationService simulationService;
    private final CsvPriceDataLoader csvLoader;

    /**
     * Run a simulation with generated price scenarios.
     */
    @PostMapping("/run")
    public ResponseEntity<SimulationResult> runSimulation(
            @RequestBody SimulationRequest request) {

        SimulationConfig config = buildConfigFromRequest(request);
        SimulationResult result = simulationService.runSimulation(config);
        log.info("Simulation completed: {} days, {} partitions, {:.2f}% return",
                result.getTotalDaysSimulated(), result.getPartitionsCreated(), result.getOverallReturnPct());

        return ResponseEntity.ok(result);
    }

    /**
     * Run a simulation with historical OHLC data from CSV file.
     */
    @PostMapping(value = "/run-historical", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SimulationResult> runHistoricalSimulation(
            @RequestPart("file") MultipartFile file,
            @RequestPart("config") SimulationRequest request) throws IOException {

        // Load OHLC data from uploaded file
        List<OhlcData> ohlcData = csvLoader.loadFromStream(file.getInputStream());
        log.info("Loaded {} days of historical data from uploaded CSV", ohlcData.size());

        // Build config with historical data
        SimulationConfig config = buildConfigFromRequest(request);
        SimulationConfig historicalConfig = SimulationConfig.builder()
                .totalCapital(config.getTotalCapital())
                .convictionPeriodYears(config.getConvictionPeriodYears())
                .stockType(config.getStockType())
                .baseConvictionScore(config.getBaseConvictionScore())
                .deploymentStyle(config.getDeploymentStyle())
                .initialPrice(ohlcData.get(0).getClose())
                .maxPartitions(config.getMaxPartitions())
                .expectedPartitionDays(config.getExpectedPartitionDays())
                .scenario(SimulationConfig.PriceScenario.HISTORICAL)
                .historicalData(ohlcData)
                .executionPriceType(request.getExecutionPriceType() != null
                        ? SimulationConfig.ExecutionPriceType.valueOf(request.getExecutionPriceType().toUpperCase())
                        : SimulationConfig.ExecutionPriceType.CLOSE)
                .lockInReferenceType(request.getLockInReferenceType() != null
                        ? SimulationConfig.LockInReferenceType.valueOf(request.getLockInReferenceType().toUpperCase())
                        : SimulationConfig.LockInReferenceType.PREV_CLOSE)
                .build();

        SimulationResult result = simulationService.runSimulation(historicalConfig);
        log.info("Historical simulation completed: {} days, {} partitions, {:.2f}% return",
                result.getTotalDaysSimulated(), result.getPartitionsCreated(), result.getOverallReturnPct());

        return ResponseEntity.ok(result);
    }

    /**
     * Run a simulation with historical data from a file path (server-side).
     */
    @PostMapping("/run-from-file")
    public ResponseEntity<SimulationResult> runFromFile(
            @RequestBody SimulationRequest request) {

        if (request.getCsvFilePath() == null || request.getCsvFilePath().isEmpty()) {
            throw new IllegalArgumentException("csvFilePath is required");
        }

        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(request.getTotalCapital() != null ? request.getTotalCapital() : 100000.0)
                .convictionPeriodYears(request.getConvictionYears() != null ? request.getConvictionYears() : 3.0)
                .stockType(request.getStockType() != null
                        ? StockType.fromKey(request.getStockType())
                        : StockType.MIDCAP)
                .baseConvictionScore(request.getConvictionScore() != null ? request.getConvictionScore() : 80)
                .deploymentStyle(request.getDeploymentStyle() != null ? request.getDeploymentStyle() : "MODERATE")
                .maxPartitions(request.getMaxPartitions() != null ? request.getMaxPartitions() : 10)
                .expectedPartitionDays(request.getExpectedDays() != null ? request.getExpectedDays() : 60)
                .scenario(SimulationConfig.PriceScenario.HISTORICAL)
                .csvFilePath(request.getCsvFilePath())
                .executionPriceType(request.getExecutionPriceType() != null
                        ? SimulationConfig.ExecutionPriceType.valueOf(request.getExecutionPriceType().toUpperCase())
                        : SimulationConfig.ExecutionPriceType.CLOSE)
                .lockInReferenceType(request.getLockInReferenceType() != null
                        ? SimulationConfig.LockInReferenceType.valueOf(request.getLockInReferenceType().toUpperCase())
                        : SimulationConfig.LockInReferenceType.PREV_CLOSE)
                .build();

        SimulationResult result = simulationService.runSimulation(config);
        return ResponseEntity.ok(result);
    }

    /**
     * Run multiple scenarios and compare results.
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, SimulationResult>> compareScenarios(
            @RequestBody SimulationRequest baseRequest) {

        SimulationConfig.SimulationConfigBuilder baseBuilder = SimulationConfig.builder()
                .totalCapital(baseRequest.getTotalCapital() != null ? baseRequest.getTotalCapital() : 100000.0)
                .convictionPeriodYears(baseRequest.getConvictionYears() != null ? baseRequest.getConvictionYears() : 3.0)
                .stockType(baseRequest.getStockType() != null
                        ? StockType.fromKey(baseRequest.getStockType())
                        : StockType.MIDCAP)
                .baseConvictionScore(baseRequest.getConvictionScore() != null ? baseRequest.getConvictionScore() : 80)
                .deploymentStyle(baseRequest.getDeploymentStyle() != null ? baseRequest.getDeploymentStyle() : "MODERATE")
                .initialPrice(baseRequest.getInitialPrice() != null ? baseRequest.getInitialPrice() : 100.0)
                .maxPartitions(baseRequest.getMaxPartitions() != null ? baseRequest.getMaxPartitions() : 10)
                .expectedPartitionDays(baseRequest.getExpectedDays() != null ? baseRequest.getExpectedDays() : 60)
                .randomSeed(baseRequest.getRandomSeed() != null ? baseRequest.getRandomSeed() : 42L);

        // Run each scenario with same seed for fair comparison
        Map<String, SimulationResult> results = new java.util.LinkedHashMap<>();

        for (SimulationConfig.PriceScenario scenario : SimulationConfig.PriceScenario.values()) {
            if (scenario != SimulationConfig.PriceScenario.CUSTOM && scenario != SimulationConfig.PriceScenario.HISTORICAL) {
                SimulationConfig config = baseBuilder.scenario(scenario).build();
                results.put(scenario.name(), simulationService.runSimulation(config));
            }
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Get a text-based simulation report.
     */
    @PostMapping("/report")
    public ResponseEntity<String> getReport(@RequestBody SimulationRequest request) {
        SimulationConfig config = buildConfigFromRequest(request);
        SimulationResult result = simulationService.runSimulation(config);
        return ResponseEntity.ok(result.generateReport());
    }

    /**
     * Get a text-based report for historical simulation.
     */
    @PostMapping(value = "/report-historical", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> getHistoricalReport(
            @RequestPart("file") MultipartFile file,
            @RequestPart("config") SimulationRequest request) throws IOException {

        List<OhlcData> ohlcData = csvLoader.loadFromStream(file.getInputStream());

        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(request.getTotalCapital() != null ? request.getTotalCapital() : 100000.0)
                .convictionPeriodYears(request.getConvictionYears() != null ? request.getConvictionYears() : 3.0)
                .stockType(request.getStockType() != null
                        ? StockType.fromKey(request.getStockType())
                        : StockType.MIDCAP)
                .baseConvictionScore(request.getConvictionScore() != null ? request.getConvictionScore() : 80)
                .deploymentStyle(request.getDeploymentStyle() != null ? request.getDeploymentStyle() : "MODERATE")
                .initialPrice(ohlcData.get(0).getClose())
                .maxPartitions(request.getMaxPartitions() != null ? request.getMaxPartitions() : 10)
                .scenario(SimulationConfig.PriceScenario.HISTORICAL)
                .historicalData(ohlcData)
                .executionPriceType(request.getExecutionPriceType() != null
                        ? SimulationConfig.ExecutionPriceType.valueOf(request.getExecutionPriceType().toUpperCase())
                        : SimulationConfig.ExecutionPriceType.CLOSE)
                .build();

        SimulationResult result = simulationService.runSimulation(config);
        return ResponseEntity.ok(result.generateReport());
    }

    private SimulationConfig buildConfigFromRequest(SimulationRequest request) {
        return SimulationConfig.builder()
                .totalCapital(request.getTotalCapital() != null ? request.getTotalCapital() : 100000.0)
                .convictionPeriodYears(request.getConvictionYears() != null ? request.getConvictionYears() : 3.0)
                .stockType(request.getStockType() != null
                        ? StockType.fromKey(request.getStockType())
                        : StockType.MIDCAP)
                .baseConvictionScore(request.getConvictionScore() != null ? request.getConvictionScore() : 80)
                .deploymentStyle(request.getDeploymentStyle() != null ? request.getDeploymentStyle() : "MODERATE")
                .initialPrice(request.getInitialPrice() != null ? request.getInitialPrice() : 100.0)
                .maxPartitions(request.getMaxPartitions() != null ? request.getMaxPartitions() : 10)
                .expectedPartitionDays(request.getExpectedDays() != null ? request.getExpectedDays() : 60)
                .scenario(request.getScenario() != null
                        ? SimulationConfig.PriceScenario.valueOf(request.getScenario().toUpperCase())
                        : SimulationConfig.PriceScenario.RANDOM)
                .customPriceChanges(request.getCustomPriceChanges())
                .randomSeed(request.getRandomSeed())
                .build();
    }

    /**
     * Request DTO for simulation endpoints.
     */
    @lombok.Data
    public static class SimulationRequest {
        private Double totalCapital;
        private Double convictionYears;
        private String stockType;  // PENNY, MIDCAP, LARGECAP, ETF
        private Integer convictionScore;
        private String deploymentStyle;  // AGGRESSIVE, MODERATE, GRADUAL
        private Double initialPrice;
        private Integer maxPartitions;
        private Integer expectedDays;
        private String scenario;  // RANDOM, BULL, BEAR, VOLATILE, SIDEWAYS, CUSTOM, HISTORICAL
        private List<Double> customPriceChanges;
        private Long randomSeed;
        private String csvFilePath;  // For server-side file loading
        private String executionPriceType;  // OPEN, CLOSE, VWAP, LOW, HIGH
        private String lockInReferenceType;  // PREV_CLOSE, OPEN
    }
}
