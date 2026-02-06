package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipExecution;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.model.PartitionEndDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PartitionLifecyclePolicy {

    private final DsipProperties dsipProperties;

    public PartitionEndDecision evaluate(DsipTracker tracker, DsipPartition partition,
            List<DsipExecution> partitionExecutions) {
        if (partitionExecutions == null || partitionExecutions.isEmpty()) {
            return PartitionEndDecision.continueRunning();
        }

        Double currentPrice = partitionExecutions.get(partitionExecutions.size() - 1).getExecutionPrice();
        long daysElapsed = computeDaysElapsed(partition);
        int expectedLength = partition.getExpectedPartitionDays();

        // Compute return progress (clamped to 0 minimum for negative returns)
        double cumulativeReturn = computeCumulativeReturn(partitionExecutions, currentPrice);
        double targetReturn = dsipProperties.getTargetReturnPerPartition();
        double returnProgress = Math.max(0.0, Math.min(1.0, cumulativeReturn / targetReturn));

        // Compute growth progress
        int growthCount = partition.getSuccessfulGrowthCount();
        int maxGrowthCount = Math.max(1, expectedLength - 1);
        double growthProgress = Math.min(1.0, (double) growthCount / maxGrowthCount);

        // Combined progress: 0.8 * returnProgress + 0.2 * growthProgress
        double returnWeight = dsipProperties.getReturnWeight();
        double growthWeight = dsipProperties.getGrowthWeight();
        double progress = returnWeight * returnProgress + growthWeight * growthProgress;

        // Capital metrics for kill switches
        double capitalProgress = computeCapitalProgress(partition);
        double remainingCapital = computeRemainingCapital(partition);
        double neutralDailyCapital = computeNeutralDailyCapital(tracker);

        // SUCCESS: progress >= 1.0 AND time >= expectedLength
        if (progress >= 1.0 && daysElapsed >= expectedLength) {
            return PartitionEndDecision.end(EndReason.SUCCESS);
        }

        // KILL_SWITCH_POOR_GROWTH: 80% capital spent, but progress <= 20%
        if (capitalProgress >= 0.8 && progress <= 0.2) {
            return PartitionEndDecision.end(EndReason.KILL_SWITCH_POOR_GROWTH);
        }

        // KILL_SWITCH_STAGNATION: 2x time elapsed, negative return
        double timeProgress = (double) daysElapsed / Math.max(1, expectedLength);
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
        Double invested = partition.getCapitalInvestedSoFar() != null ? partition.getCapitalInvestedSoFar()
                : 0.0;
        return invested / partition.getPartitionCapitalAllocated();
    }

    private double computeCumulativeReturn(List<DsipExecution> executions, Double currentPrice) {
        if (executions == null || executions.isEmpty()) {
            return 0.0;
        }

        double totalInvested = 0.0;
        double totalShares = 0.0;

        for (DsipExecution execution : executions) {
            Double amount = execution.getExecutedAmount() != null ? execution.getExecutedAmount() : 0.0;
            Double price = execution.getExecutionPrice() != null ? execution.getExecutionPrice() : 0.0;
            totalInvested += amount;
            if (price > 0) {
                totalShares += amount / price;
            }
        }

        if (totalInvested == 0.0) {
            return 0.0;
        }

        double currentValue = totalShares * currentPrice;
        return (currentValue - totalInvested) / totalInvested;
    }

    private double computeRemainingCapital(DsipPartition partition) {
        Double invested = partition.getCapitalInvestedSoFar() != null ? partition.getCapitalInvestedSoFar()
                : 0.0;
        return partition.getPartitionCapitalAllocated() - invested;
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
