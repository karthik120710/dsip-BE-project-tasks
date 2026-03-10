package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.model.PartitionExecutionPlan;
import com.dsip.backend.model.PartitionPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.util.FinancialCalculator;

@Component
@RequiredArgsConstructor
public class PartitionAllocationPolicy {

    private final DsipProperties dsipProperties;
    private final FinancialCalculator financialCalculator;

    public PartitionPlan createFirstPlan(DsipTracker tracker) {
        return createPlan(tracker, 1, Collections.emptyList());
    }

    public PartitionPlan createPlan(DsipTracker tracker, int nextPartitionIndex, List<DsipPartition> pastPartitions) {

        List<PartitionExecutionPlan> fullPlan = generatePartitionExecutionPlan(tracker);
        PartitionExecutionPlan planItem = fullPlan.stream()
                .filter(p -> p.getPartitionNumber() == nextPartitionIndex)
                .findFirst()
                .orElse(null);

        int expectedPartitionDays = resolveExpectedLength(tracker.getPartitionDays(), pastPartitions);
        double allocatedCapital = calculateDynamicAllocation(tracker, nextPartitionIndex, fullPlan, planItem);

        return new PartitionPlan(nextPartitionIndex, expectedPartitionDays, allocatedCapital);
    }

    private double calculateDynamicAllocation(DsipTracker tracker, int nextPartitionIndex,
                                              List<PartitionExecutionPlan> fullPlan,
                                              PartitionExecutionPlan planItem) {
        if (planItem == null || fullPlan.isEmpty() || tracker == null || tracker.getTotalCapitalPlanned() == null) {
            return 0.0;
        }

        double totalCapital = tracker.getTotalCapitalPlanned();
        double investedSoFar = tracker.getTotalCapitalInvestedSoFar() != null ? tracker.getTotalCapitalInvestedSoFar() : 0.0;
        double remainingCapital = Math.max(0.0, totalCapital - investedSoFar);

        if (remainingCapital <= 0.0) {
            return 0.0;
        }

        int totalPartitions = fullPlan.size();
        if (nextPartitionIndex > totalPartitions) {
            return 0.0;
        }

        // Calculate sum of planned amount for remaining partitions from nextPartition onwards.
        double remainingPlanned = fullPlan.stream()
                .filter(p -> p.getPartitionNumber() >= nextPartitionIndex)
                .mapToDouble(PartitionExecutionPlan::getAllocatedAmount)
                .sum();

        if (remainingPlanned <= 0.0) {
            // fallback to hard plan value for this partition
            return financialCalculator.round(planItem.getAllocatedAmount(), 2);
        }

        // For the last partition, consume all remaining capital exactly (prevents rounding drift).
        if (nextPartitionIndex == totalPartitions) {
            return financialCalculator.round(remainingCapital, 2);
        }

        double scaled = remainingCapital * (planItem.getAllocatedAmount() / remainingPlanned);
        return financialCalculator.round(scaled, 2);
    }

    private void validateTrackerForPartitionPlan(DsipTracker tracker) {
        if (tracker == null) {
            throw new IllegalArgumentException("Tracker cannot be null");
        }

        if (tracker.getTotalCapitalPlanned() == null || tracker.getTotalCapitalPlanned() <= 0) {
            throw new IllegalArgumentException("Budget must be greater than 0");
        }

        if (tracker.getConvictionPeriodYears() == null || tracker.getConvictionPeriodYears() <= 0) {
            throw new IllegalArgumentException("Conviction months must be greater than 0");
        }

        if (tracker.getPartitionDays() == null || tracker.getPartitionDays() <= 0) {
            throw new IllegalArgumentException("Partition length must be greater than 0");
        }

        double convictionDays = tracker.getConvictionPeriodYears() * dsipProperties.getTradingDaysPerYear();
        if (tracker.getPartitionDays() > convictionDays) {
            throw new IllegalArgumentException("Partition length cannot exceed conviction period");
        }

        try {
            DeploymentStyle.fromValue(tracker.getDeploymentStyle());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Strategy must be valid", ex);
        }
    }

    public List<PartitionExecutionPlan> generatePartitionExecutionPlan(DsipTracker tracker) {
        validateTrackerForPartitionPlan(tracker);

        int totalPartitions = calculateTotalPartitions(tracker);
        int phaseCount = Math.max(1, dsipProperties.getPhaseCount());

        int[] partitionsPerPhase = new int[phaseCount];
        for (int idx = 0; idx < totalPartitions; idx++) {
            partitionsPerPhase[idx % phaseCount]++;
        }

        List<Double> phaseWeights = dsipProperties.getLoadFactor()
                .getByDeploymentStyle(DeploymentStyle.fromValue(tracker.getDeploymentStyle()));
        if (phaseWeights == null || phaseWeights.size() < phaseCount) {
            phaseWeights = new ArrayList<>();
            for (int i = 0; i < phaseCount; i++) {
                phaseWeights.add(1.0 / phaseCount);
            }
        }

        double totalCapital = tracker.getTotalCapitalPlanned() != null ? tracker.getTotalCapitalPlanned() : 0.0;

        List<PartitionExecutionPlan> plan = new ArrayList<>();
        double totalAllocated = 0.0;

        for (int partitionNumber = 1; partitionNumber <= totalPartitions; partitionNumber++) {
            int phaseIdx = (partitionNumber - 1) % phaseCount;
            int phaseNumber = phaseIdx + 1;
            int slotsInPhase = Math.max(1, partitionsPerPhase[phaseIdx]);

            double weight = phaseIdx < phaseWeights.size() ? phaseWeights.get(phaseIdx) : 1.0 / phaseCount;
            double amountExact = (totalCapital * weight) / slotsInPhase;
            double amountRounded = financialCalculator.round(amountExact, 2);

            plan.add(new PartitionExecutionPlan(partitionNumber, phaseNumber, amountRounded));
            totalAllocated += amountRounded;
        }

        // Fix any rounding drift so sum of allocated amounts is exactly totalCapital (up to 2 decimals)
        double targetTotal = financialCalculator.round(totalCapital, 2);
        double drift = financialCalculator.round(targetTotal - totalAllocated, 2);
        if (Math.abs(drift) >= 0.01 && !plan.isEmpty()) {
            PartitionExecutionPlan last = plan.get(plan.size() - 1);
            last.setAllocatedAmount(financialCalculator.round(last.getAllocatedAmount() + drift, 2));
        }

        return plan;
    }

    private int calculateTotalPartitions(DsipTracker tracker) {
        if (tracker.getConvictionPeriodYears() == null || tracker.getConvictionPeriodYears() <= 0.0
                || tracker.getPartitionDays() == null || tracker.getPartitionDays() <= 0) {
            return 1;
        }

        double convictionMonths = tracker.getConvictionPeriodYears() * 12.0;
        double partitionLengthMonths = (tracker.getPartitionDays() / (double) dsipProperties.getTradingDaysPerYear())
                * 12.0;

        if (partitionLengthMonths <= 0.0) {
            return 1;
        }

        int partitionCount = (int) Math.max(1, Math.round(convictionMonths / partitionLengthMonths));
        return partitionCount;
    }

    private int resolveExpectedLength(int defaultPartitionDays, List<DsipPartition> pastPartitions) {
        if (pastPartitions == null || pastPartitions.isEmpty()) {
            return defaultPartitionDays;
        }
        List<Integer> sorted = getPartitionDays(pastPartitions);
        int size = sorted.size();

        // If no completed partitions have valid durations, use default
        if (size == 0) {
            return defaultPartitionDays;
        }

        if (size == 1) {
            return sorted.get(0);
        }

        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
        } else {
            return sorted.get(size / 2);
        }
    }

    private List<Integer> getPartitionDays(List<DsipPartition> pastPartitions) {
        List<Integer> sorted = pastPartitions.stream()
                .map(p -> financialCalculator.calculateDaysBetween(p.getCreatedAt(),
                        p.getPartitionEndDate()))
                .filter(d -> d > 0)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        return sorted;
    }

    private int computeTotalPartitions(DsipTracker tracker, List<DsipPartition> pastPartitions) {

        double pastPartitionsMedianLength = resolveExpectedLength(tracker.getPartitionDays(), pastPartitions);
        int totalDaysPassed = getPartitionDays(pastPartitions).stream().mapToInt(Integer::intValue).sum();
        double trackerDays = tracker.getConvictionPeriodYears() * dsipProperties.getTradingDaysPerYear();
        double remainingTime = trackerDays - totalDaysPassed;

        return (int) Math.round(remainingTime / pastPartitionsMedianLength) + pastPartitions.size();
    }

    private double resolvePhaseWeight(DsipTracker tracker, List<DsipPartition> pastPartitions) {
        DeploymentStyle style = DeploymentStyle.fromValue(tracker.getDeploymentStyle());
        List<Double> weights = dsipProperties.getLoadFactor().getByKey(style.getKey());

        // If no past partitions, use first phase weight
        if (pastPartitions == null || pastPartitions.isEmpty()) {
            return weights.get(0);
        }

        double medianBurned = tracker.getTotalCapitalInvestedSoFar() / pastPartitions.size();
        double capitalRemaining = tracker.getTotalCapitalPlanned() - tracker.getTotalCapitalInvestedSoFar();
        double deployedPercentage = 100 * tracker.getTotalCapitalInvestedSoFar() / tracker.getTotalCapitalPlanned();

        boolean hasAmountForPhase1 = (weights.get(0)) * capitalRemaining >= medianBurned;
        boolean hasAmountForPhase2 = (weights.get(1) + weights.get(0)) * capitalRemaining >= medianBurned;

        if (deployedPercentage < weights.get(0) && hasAmountForPhase1) {
            return weights.get(0);
        } else if (deployedPercentage < (weights.get(1) + weights.get(0)) && hasAmountForPhase2) {
            return weights.get(1);
        } else {
            return weights.get(2);
        }
    }

    private int computeAllocatedCapital(DsipTracker tracker, int totalPartitions, double phaseWeight) {

        int partitionsPerPhase = Math.max(1, totalPartitions / dsipProperties.getPhaseCount());
        return (int) Math.round((double) tracker.getTotalCapitalPlanned() * phaseWeight / partitionsPerPhase);
    }
}