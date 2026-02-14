package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.model.PartitionPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

        double phaseWeight = resolvePhaseWeight(tracker, pastPartitions);
        int totalPartitions = computeTotalPartitions(tracker, pastPartitions);
        int allocatedCapital = computeAllocatedCapital(tracker, totalPartitions, phaseWeight);
        int expectedPartitionDays = resolveExpectedLength(tracker.getPartitionDays(), pastPartitions);

        return new PartitionPlan(nextPartitionIndex, expectedPartitionDays, allocatedCapital);

    }

    private int resolveExpectedLength(int defaultPartitionDays, List<DsipPartition> pastPartitions) {
        if (pastPartitions == null || pastPartitions.isEmpty()) {
            return defaultPartitionDays;
        }
        List<Integer> sorted = getPartitionDays(pastPartitions);
        int size = sorted.size();
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

        return (int) Math.floor(remainingTime / pastPartitionsMedianLength) + pastPartitions.size();
    }

    private double resolvePhaseWeight(DsipTracker tracker, List<DsipPartition> pastPartitions) {
        DeploymentStyle style = DeploymentStyle.fromValue(tracker.getDeploymentStyle());
        List<Double> weights = dsipProperties.getLoadFactor().getByKey(style.getKey());

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