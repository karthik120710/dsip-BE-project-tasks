package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.model.PartitionPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PartitionAllocationPolicy {

    private final DsipProperties dsipProperties;

    public PartitionPlan createFirstPlan(DsipTracker tracker) {
        return createPlan(tracker, 1, Collections.emptyList());
    }

    public PartitionPlan createPlan(DsipTracker tracker, int nextPartitionIndex, List<Integer> pastPartitionLengths) {
        int expectedLengthDays = resolveExpectedLength(tracker.getPartitionDays(), pastPartitionLengths);
        int totalPartitions = computeTotalPartitions(tracker);
        int phaseIndex = computePhaseIndex(nextPartitionIndex, totalPartitions);
        double phaseWeight = resolvePhaseWeight(tracker.getDeploymentStyle(), phaseIndex);
        int allocatedCapital = computeAllocatedCapital(tracker, totalPartitions, phaseWeight);

        return new PartitionPlan(nextPartitionIndex, expectedLengthDays, phaseIndex, allocatedCapital);
    }

    private int resolveExpectedLength(int defaultPartitionDays, List<Integer> pastPartitionLengths) {
        if (pastPartitionLengths == null || pastPartitionLengths.isEmpty()) {
            return defaultPartitionDays;
        }
        List<Integer> sorted = pastPartitionLengths.stream().sorted().toList();
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
        } else {
            return sorted.get(size / 2);
        }
    }

    private int computeTotalPartitions(DsipTracker tracker) {
        int tradingDaysPerYear = dsipProperties.getTradingDaysPerYear();
        int totalTradingDays = tracker.getConvictionPeriodYears() * tradingDaysPerYear;
        return Math.max(1, totalTradingDays / tracker.getPartitionDays());
    }

    private int computePhaseIndex(int partitionIndex, int totalPartitions) {
        int phaseCount = dsipProperties.getPhaseCount();
        int partitionsPerPhase = Math.max(1, totalPartitions / phaseCount);
        int phaseIndex = (partitionIndex - 1) / partitionsPerPhase;
        return Math.min(phaseIndex, phaseCount - 1);
    }

    private double resolvePhaseWeight(Integer deploymentStyleValue, int phaseIndex) {
        DeploymentStyle style = DeploymentStyle.fromValue(deploymentStyleValue);
        List<Double> weights = dsipProperties.getLoadFactor().getByKey(style.getKey());
        if (weights == null || weights.isEmpty()) {
            return 1.0;
        }
        if (phaseIndex >= weights.size()) {
            return weights.get(weights.size() - 1);
        }
        return weights.get(phaseIndex);
    }

    private int computeAllocatedCapital(DsipTracker tracker, int totalPartitions, double phaseWeight) {
        double neutralCapital = (double) tracker.getTotalCapitalPlanned() / totalPartitions;
        return (int) Math.round(neutralCapital * phaseWeight);
    }
}
