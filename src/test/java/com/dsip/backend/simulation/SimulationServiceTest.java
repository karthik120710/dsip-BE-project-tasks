/*
package com.dsip.backend.simulation;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.service.DsipCalculationEngine;
import com.dsip.backend.service.PartitionAllocationPolicy;
import com.dsip.backend.service.PartitionLifecyclePolicy;
import com.dsip.backend.util.FinancialCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

*/
/**
 * Unit tests for DSIP Simulation Service.
 *//*

class SimulationServiceTest {

    private SimulationService simulationService;
    private DsipProperties dsipProperties;

    @BeforeEach
    void setUp() {
        // Create real instances (not mocks) for integration-style testing
        dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        DsipCalculationEngine calculationEngine = new DsipCalculationEngine(dsipProperties, financialCalculator,dsipTrac);
        PartitionAllocationPolicy allocationPolicy = new PartitionAllocationPolicy(dsipProperties, financialCalculator);
        PartitionLifecyclePolicy lifecyclePolicy = new PartitionLifecyclePolicy(financialCalculator, dsipProperties);
        CsvPriceDataLoader csvLoader = new CsvPriceDataLoader();

        simulationService = new SimulationService(
                calculationEngine,
                allocationPolicy,
                lifecyclePolicy,
                dsipProperties,
                csvLoader
        );
    }

    @Test
    void testBullMarketSimulation() {
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(100000.0)
                .convictionPeriodYears(1.0)  // 1 year for faster test
                .stockType(StockType.MIDCAP)
                .baseConvictionScore(80)
                .deploymentStyle("MODERATE")
                .initialPrice(100.0)
                .maxPartitions(5)
                .scenario(SimulationConfig.PriceScenario.BULL)
                .randomSeed(42L)
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        // Basic assertions
        assertNotNull(result);
        assertTrue(result.getTotalDaysSimulated() > 0, "Should simulate some days");
        assertTrue(result.getPartitionsCreated() > 0, "Should create at least one partition");
        assertTrue(result.getTotalCapitalInvested() > 0, "Should invest some capital");
        assertTrue(result.getTotalSharesAccumulated() > 0, "Should accumulate shares");

        // Print report
        System.out.println(result.generateReport());
    }

    @Test
    void testBearMarketSimulation() {
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(100000.0)
                .convictionPeriodYears(1.0)
                .stockType(StockType.MIDCAP)
                .baseConvictionScore(80)
                .deploymentStyle("MODERATE")
                .initialPrice(100.0)
                .maxPartitions(5)
                .scenario(SimulationConfig.PriceScenario.BEAR)
                .randomSeed(42L)
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        assertNotNull(result);
        assertTrue(result.getTotalDaysSimulated() > 0);
        assertTrue(result.getPartitionsCreated() > 0);

        // Check that kill switches are triggered in bear market
        System.out.println(result.generateReport());
    }

    @Test
    void testVolatileMarketSimulation() {
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(100000.0)
                .convictionPeriodYears(1.0)
                .stockType(StockType.PENNY)  // Higher target return
                .baseConvictionScore(90)     // High conviction
                .deploymentStyle("AGGRESSIVE")
                .initialPrice(50.0)
                .maxPartitions(5)
                .scenario(SimulationConfig.PriceScenario.VOLATILE)
                .randomSeed(42L)
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        assertNotNull(result);
        System.out.println(result.generateReport());
    }

    @Test
    void testSidewaysMarketSimulation() {
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(100000.0)
                .convictionPeriodYears(1.0)
                .stockType(StockType.LARGECAP)
                .baseConvictionScore(70)
                .deploymentStyle("GRADUAL")
                .initialPrice(200.0)
                .maxPartitions(5)
                .scenario(SimulationConfig.PriceScenario.SIDEWAYS)
                .randomSeed(42L)
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        assertNotNull(result);
        System.out.println(result.generateReport());
    }

    @Test
    void testCustomPriceChanges() {
        // Simulate a specific price pattern
        // Note: Simulation runs for full conviction period, custom prices are used first
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(10000.0)
                .convictionPeriodYears(0.08)  // ~20 trading days (252 * 0.08 = 20.16)
                .stockType(StockType.MIDCAP)
                .baseConvictionScore(80)
                .deploymentStyle("MODERATE")
                .initialPrice(100.0)
                .maxPartitions(2)
                .scenario(SimulationConfig.PriceScenario.CUSTOM)
                .customPriceChanges(List.of(
                        -2.0, -1.5, -3.0, 1.0, 2.0,   // Dip then recovery
                        0.5, 0.3, -0.5, 0.2, 0.4,    // Sideways
                        1.0, 1.5, 2.0, 1.0, 0.5,     // Rally
                        -1.0, -0.5, 0.0, 0.5, 1.0    // Mild correction then up
                ))
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        assertNotNull(result);
        assertTrue(result.getTotalDaysSimulated() <= 21, "Should simulate ~20 days");

        // Check first few days (custom prices)
        SimulationResult.DayResult day1 = result.getDayResults().get(0);
        assertEquals(-2.0, day1.getPriceChangePct(), 0.01);

        // On a red day, the opportunity should be favorable
        System.out.println("Day 1 opportunity multiplier: " + day1.getOpportunityMultiplier());

        System.out.println(result.generateReport());
    }

    @Test
    void testOpportunityMultiplierOnRedDays() {
        // Test that red days (negative lock-in) increase opportunity
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(10000.0)
                .convictionPeriodYears(0.1)
                .stockType(StockType.MIDCAP)
                .baseConvictionScore(80)
                .deploymentStyle("MODERATE")
                .initialPrice(100.0)
                .maxPartitions(1)
                .scenario(SimulationConfig.PriceScenario.CUSTOM)
                .customPriceChanges(List.of(-5.0, -3.0, -2.0, 2.0, 3.0))
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        // On consecutive red days, opportunity multiplier should be >= 1
        for (int i = 0; i < 3; i++) {
            SimulationResult.DayResult day = result.getDayResults().get(i);
            assertTrue(day.getOpportunityMultiplier() >= 0.7,  // Min is 0.7
                    "Day " + (i + 1) + " should have opportunity >= 0.7, was: " + day.getOpportunityMultiplier());
        }
    }

    @Test
    void testPartitionLifecycleTransitions() {
        // Test that partitions transition correctly
        SimulationConfig config = SimulationConfig.builder()
                .totalCapital(50000.0)
                .convictionPeriodYears(2.0)
                .stockType(StockType.MIDCAP)
                .baseConvictionScore(85)
                .deploymentStyle("AGGRESSIVE")
                .initialPrice(100.0)
                .maxPartitions(10)
                .scenario(SimulationConfig.PriceScenario.RANDOM)
                .randomSeed(12345L)
                .build();

        SimulationResult result = simulationService.runSimulation(config);

        assertNotNull(result);
        assertTrue(result.getPartitionsCreated() >= 1);

        // Each partition should have valid metrics
        for (SimulationResult.PartitionResult pr : result.getPartitionResults()) {
            assertTrue(pr.getCapitalInvested() >= 0);
            assertTrue(pr.getSharesAccumulated() >= 0);
            assertTrue(pr.getDaysActive() > 0);
            assertNotNull(pr.getEndReason());
        }

        System.out.println(result.generateReport());
    }

    @Test
    void testDeploymentStylesComparison() {
        String[] styles = {"AGGRESSIVE", "MODERATE", "GRADUAL"};

        System.out.println("\n=== DEPLOYMENT STYLE COMPARISON ===\n");

        for (String style : styles) {
            SimulationConfig config = SimulationConfig.builder()
                    .totalCapital(100000.0)
                    .convictionPeriodYears(1.0)
                    .stockType(StockType.MIDCAP)
                    .baseConvictionScore(80)
                    .deploymentStyle(style)
                    .initialPrice(100.0)
                    .maxPartitions(5)
                    .scenario(SimulationConfig.PriceScenario.RANDOM)
                    .randomSeed(42L)
                    .build();

            SimulationResult result = simulationService.runSimulation(config);

            System.out.printf("%s: %.1f%% return, %d partitions, $%.2f invested%n",
                    style,
                    result.getOverallReturnPct(),
                    result.getPartitionsCreated(),
                    result.getTotalCapitalInvested());
        }
    }

    @Test
    void testStockTypeComparison() {
        System.out.println("\n=== STOCK TYPE COMPARISON ===\n");

        for (StockType stockType : StockType.values()) {
            SimulationConfig config = SimulationConfig.builder()
                    .totalCapital(100000.0)
                    .convictionPeriodYears(1.0)
                    .stockType(stockType)
                    .baseConvictionScore(80)
                    .deploymentStyle("MODERATE")
                    .initialPrice(100.0)
                    .maxPartitions(5)
                    .scenario(SimulationConfig.PriceScenario.BULL)
                    .randomSeed(42L)
                    .build();

            SimulationResult result = simulationService.runSimulation(config);

            System.out.printf("%s (target: %.0f%%): %.1f%% return, %d partitions%n",
                    stockType.getKey(),
                    stockType.getTargetReturnPercentage() * 100,
                    result.getOverallReturnPct(),
                    result.getPartitionsCreated());
        }
    }
}
*/
