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

    @Test
    void testCreatePlanScalesWithRemainingCapitalAfterPartialPhase1() {
        DsipProperties dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        PartitionAllocationPolicy policy = new PartitionAllocationPolicy(dsipProperties, financialCalculator);

        DsipTracker tracker = DsipTracker.builder()
                .convictionPeriodYears(0.25) // 3 months
                .partitionDays(21) // monthly
                .totalCapitalPlanned(100.0)
                .deploymentStyle(3) // AGGRESSIVE
                .totalCapitalInvestedSoFar(30.0)
                .build();

        // Next partition is #2 (phase 2) for as-if first phase ended after 30 deployed
        PartitionPlan next = policy.createPlan(tracker, 2, List.of());

        // full aggressive 3-month plan would be approx [51,24,25] (after drift correction)
        // remaining capital = 70, remaining planned sum = 49, so this partition gets ~34.29
        assertEquals(2, next.getPartitionIndex());
        assertEquals(34.29, next.getAllocatedCapital(), 0.01);
    }

    @Test
    void testValidationFailsForBadInputs() {
        DsipProperties dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        PartitionAllocationPolicy policy = new PartitionAllocationPolicy(dsipProperties, financialCalculator);

        DsipTracker invalidBudget = DsipTracker.builder()
                .convictionPeriodYears(1.0)
                .partitionDays(21)
                .totalCapitalPlanned(0.0)
                .deploymentStyle(2)
                .build();

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> policy.generatePartitionExecutionPlan(invalidBudget));
        assertEquals("Budget must be greater than 0", ex1.getMessage());

        DsipTracker invalidConviction = DsipTracker.builder()
                .convictionPeriodYears(0.0)
                .partitionDays(21)
                .totalCapitalPlanned(1000.0)
                .deploymentStyle(2)
                .build();

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> policy.generatePartitionExecutionPlan(invalidConviction));
        assertEquals("Conviction months must be greater than 0", ex2.getMessage());

        DsipTracker invalidPartitionLength = DsipTracker.builder()
                .convictionPeriodYears(1.0)
                .partitionDays(0)
                .totalCapitalPlanned(1000.0)
                .deploymentStyle(2)
                .build();

        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> policy.generatePartitionExecutionPlan(invalidPartitionLength));
        assertEquals("Partition length must be greater than 0", ex3.getMessage());

        DsipTracker invalidTooLong = DsipTracker.builder()
                .convictionPeriodYears(0.5)
                .partitionDays(21)
                .totalCapitalPlanned(1000.0)
                .deploymentStyle(2)
                .build();

        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class,
                () -> policy.generatePartitionExecutionPlan(invalidTooLong));
        assertEquals("Partition length cannot exceed conviction period", ex4.getMessage());

        DsipTracker invalidStrategy = DsipTracker.builder()
                .convictionPeriodYears(1.0)
                .partitionDays(21)
                .totalCapitalPlanned(1000.0)
                .deploymentStyle(99)
                .build();

        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class,
                () -> policy.generatePartitionExecutionPlan(invalidStrategy));
        assertEquals("Strategy must be valid", ex5.getMessage());
    }

    @Test
    void testOneMonthAggressiveRecommendationPlan() {
        DsipProperties dsipProperties = new DsipProperties();
        FinancialCalculator financialCalculator = new FinancialCalculator(dsipProperties);
        PartitionAllocationPolicy policy = new PartitionAllocationPolicy(dsipProperties, financialCalculator);

        DsipTracker tracker = DsipTracker.builder()
                .convictionPeriodYears(1.0 / 12.0)
                .partitionDays(21)
                .totalCapitalPlanned(100.0)
                .deploymentStyle(3) // AGGRESSIVE
                .build();

        List<PartitionExecutionPlan> plan = policy.generatePartitionExecutionPlan(tracker);

        assertEquals(1, plan.size(), "Only one partition expected for 1-month conviction and 1-month partition length");

        PartitionExecutionPlan p0 = plan.get(0);
        assertEquals(1, p0.getPartitionNumber());
        assertEquals(1, p0.getPhaseNumber(), "First partition should be phase 1 in a cycle");

        // AGGRESSIVE phase weights are [0.51,0.24,0.24], but with a single partition we correct drift to full capital
        assertEquals(100.0, p0.getAllocatedAmount(), 0.001);
    }
}
