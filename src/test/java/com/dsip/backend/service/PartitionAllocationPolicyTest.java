package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.model.PartitionExecutionPlan;
import com.dsip.backend.model.PartitionPlan;
import com.dsip.backend.util.FinancialCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PartitionAllocationPolicyTest {

    @Test
    void testGeneratePartitionExecutionPlanCyclesPhasesAndAllocatesCorrectly() {
        DsipProperties dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        PartitionAllocationPolicy policy = new PartitionAllocationPolicy(dsipProperties, financialCalculator);

        DsipTracker tracker = DsipTracker.builder()
                .convictionPeriodYears(1.0)
                .partitionDays(21)
                .totalCapitalPlanned(1200.0)
                .deploymentStyle(2)
                .build();

        List<PartitionExecutionPlan> plan = policy.generatePartitionExecutionPlan(tracker);

        assertEquals(12, plan.size(), "12 partitions should be created for 12 months at 1 month each");

        assertEquals(1, plan.get(0).getPhaseNumber());
        assertEquals(2, plan.get(1).getPhaseNumber());
        assertEquals(3, plan.get(2).getPhaseNumber());
        assertEquals(1, plan.get(3).getPhaseNumber());

        assertEquals(126.0, plan.get(0).getAllocatedAmount(), 0.001);
        assertEquals(99.0, plan.get(1).getAllocatedAmount(), 0.001);
        assertEquals(72.0, plan.get(2).getAllocatedAmount(), 0.001);
    }

    @Test
    void testCreatePlanUsesPartitionExecutionPlanEntry() {
        DsipProperties dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        PartitionAllocationPolicy policy = new PartitionAllocationPolicy(dsipProperties, financialCalculator);

        DsipTracker tracker = DsipTracker.builder()
                .convictionPeriodYears(1.0)
                .partitionDays(21)
                .totalCapitalPlanned(1200.0)
                .deploymentStyle(2)
                .build();

        PartitionPlan next = policy.createPlan(tracker, 5, List.of());

        assertEquals(5, next.getPartitionIndex());
        assertEquals(99.0, next.getAllocatedCapital(), 0.001);
    }
}
