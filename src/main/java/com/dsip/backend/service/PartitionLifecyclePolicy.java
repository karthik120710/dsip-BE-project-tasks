package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.model.PartitionEndDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PartitionLifecyclePolicy {

    private final DsipProperties dsipProperties;

    public PartitionEndDecision evaluate(DsipTracker tracker, DsipPartition partition, List<DsipExecution> partitionExecutions) {
        if (partitionExecutions == null || partitionExecutions.isEmpty()) {
            return PartitionEndDecision.continueRunning();
        }

        long daysElapsed = computeDaysElapsed(partition);
        double capitalProgress = computeCapitalProgress(partition);
        double timeProgress = computeTimeProgress(partition, daysElapsed);
        double cumulativeReturn = computeCumulativeReturn(partitionExecutions);
        double targetReturn = computeTargetReturn(partition, daysElapsed);
        double remainingCapital = computeRemainingCapital(partition);
        double neutralDailyCapital = computeNeutralDailyCapital(tracker);

        // SUCCESS: progress >= 0.8 AND (time threshold OR capital threshold)
        if (capitalProgress >= 0.8 && (timeProgress >= 1.0 || capitalProgress >= 1.0)) {
            return PartitionEndDecision.end(EndReason.SUCCESS);
        }

        // KILL_SWITCH_POOR_GROWTH: 80% capital spent, but only 20% of expected progress
        if (capitalProgress >= 0.8 && cumulativeReturn < targetReturn * 0.2) {
            return PartitionEndDecision.end(EndReason.KILL_SWITCH_POOR_GROWTH);
        }

        // KILL_SWITCH_STAGNATION: 2x time elapsed, negative return
        if (timeProgress >= 2.0 && cumulativeReturn < 0) {
            return PartitionEndDecision.end(EndReason.KILL_SWITCH_STAGNATION);
        }

        // ZOMBIE_REMAINDER: remaining capital < neutral daily allocation
        if (remainingCapital > 0 && remainingCapital < neutralDailyCapital) {
            return PartitionEndDecision.end(EndReason.ZOMBIE_REMAINDER);
        }

        return PartitionEndDecision.continueRunning();
    }

    private long computeDaysElapsed(DsipPartition partition) {
        LocalDate startDate = partition.getCreatedAt()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return ChronoUnit.DAYS.between(startDate, today);
    }

    private double computeCapitalProgress(DsipPartition partition) {
        if (partition.getPartitionCapitalAllocated() == 0) {
            return 0.0;
        }
        return (double) partition.getCapitalInvestedSoFar() / partition.getPartitionCapitalAllocated();
    }

    private double computeTimeProgress(DsipPartition partition, long daysElapsed) {
        if (partition.getExpectedPartitionDays() == 0) {
            return 0.0;
        }
        return (double) daysElapsed / partition.getExpectedPartitionDays();
    }

    private double computeCumulativeReturn(List<DsipExecution> executions) {
        if (executions == null || executions.isEmpty()) {
            return 0.0;
        }
        int totalInvested = executions.stream()
                .mapToInt(DsipExecution::getExecutedAmount)
                .sum();
        if (totalInvested == 0) {
            return 0.0;
        }
        int latestPrice = executions.get(executions.size() - 1).getExecutionPrice();
        int totalShares = executions.stream()
                .mapToInt(e -> e.getExecutedAmount() / e.getExecutionPrice())
                .sum();
        int currentValue = totalShares * latestPrice;
        return (double) (currentValue - totalInvested) / totalInvested;
    }

    private double computeTargetReturn(DsipPartition partition, long daysElapsed) {
        int tradingDaysPerYear = dsipProperties.getTradingDaysPerYear();
        double annualizedDays = Math.max(1, daysElapsed);
        return 0.10 * (annualizedDays / tradingDaysPerYear);
    }

    private double computeRemainingCapital(DsipPartition partition) {
        return partition.getPartitionCapitalAllocated() - partition.getCapitalInvestedSoFar();
    }

    private double computeNeutralDailyCapital(DsipTracker tracker) {
        int tradingDaysPerYear = dsipProperties.getTradingDaysPerYear();
        int totalTradingDays = tracker.getConvictionPeriodYears() * tradingDaysPerYear;
        if (totalTradingDays == 0) {
            return 0.0;
        }
        return (double) tracker.getTotalCapitalPlanned() / totalTradingDays;
    }
}
