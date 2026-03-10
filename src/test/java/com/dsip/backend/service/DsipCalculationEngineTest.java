package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.util.FinancialCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DsipCalculationEngineTest {

    @Test
    void testCalculatePartitionAllocationUsesRemainingCapital() {
        DsipProperties dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        DsipTracker tracker = DsipTracker.builder()
                .convictionPeriodYears(0.25)
                .partitionDays(21)
                .totalCapitalPlanned(100.0)
                .deploymentStyle(3)
                .totalCapitalInvestedSoFar(30.0)
                .build();

        DsipCalculationEngine engine = new DsipCalculationEngine(dsipProperties, financialCalculator, null);

        double allocation = engine.calculatePartitionAllocation(tracker, 2);

        assertEquals(34.29, allocation, 0.01, "Second partition allocation should scale with remaining capital");
    }
}
