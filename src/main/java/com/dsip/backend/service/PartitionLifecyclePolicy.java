package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.enums.StockType;
import com.dsip.backend.model.PartitionEndDecision;
import com.dsip.backend.util.FinancialCalculator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PartitionLifecyclePolicy {

    private final FinancialCalculator financialCalculator;

    private final DsipProperties dsipProperties;


    public PartitionEndDecision evaluate(DsipTracker tracker, DsipPartition partition, double marketPrice) {
        return evaluate(tracker, partition, marketPrice, Instant.now());
    }

    public PartitionEndDecision evaluate(DsipTracker tracker, DsipPartition partition, double marketPrice,
            Instant asOf) {
        // Calculate all metrics once to avoid redundant calculations

        StockType stockType = StockType.fromValue(tracker.getStockType());
        double partitionProgress = financialCalculator.calculatePartitionProgressPercentage(partition, marketPrice,
                stockType);
        double timeProgress = financialCalculator.calculateTimeProgressPercentage(partition, asOf);
        double capitalProgress = financialCalculator.calculateCapitalProgressPercentage(partition);
        double cumulativeReturn = financialCalculator.cumulativeReturnPercentage(
                partition.getNoOfSharesBought(),
                partition.getCapitalInvestedSoFar(),
                marketPrice);
        double daysElapsed = financialCalculator.calculateDaysBetween(partition.getCreatedAt(), asOf);

        if (isSuccessCondition(partitionProgress, timeProgress, capitalProgress) || cumulativeReturn >= dsipProperties.targetReturnPerPartitionPercentage(stockType) )
            return PartitionEndDecision.end(EndReason.SUCCESS);

        if (isKillSwitchStagnation(timeProgress, cumulativeReturn))
            return PartitionEndDecision.end(EndReason.KILL_SWITCH_STAGNATION);

        if (isKillSwitchPoorGrowth(capitalProgress, partitionProgress))
            return PartitionEndDecision.end(EndReason.KILL_SWITCH_POOR_GROWTH);

        if (isZombieRemainder(partition, daysElapsed))
            return cumulativeReturn >= 0 ? PartitionEndDecision.end(EndReason.NEUTRAL_PARTITION)
                    : PartitionEndDecision.end(EndReason.KILL_SWITCH_ZOMBIE);

        if (isNeutralByTimeExhaustion(cumulativeReturn, timeProgress))
            return PartitionEndDecision.end(EndReason.NEUTRAL_PARTITION);

        return PartitionEndDecision.continueRunning();
    }

    private boolean isSuccessCondition(double partitionProgress, double timeProgress, double capitalProgress) {
        return partitionProgress >= 80 && (timeProgress >= 100 || capitalProgress >= 90);
    }

    private boolean isKillSwitchPoorGrowth(double capitalProgress, double partionProgress) {
        return capitalProgress >= 80 && partionProgress <= 20;
    }

    private boolean isKillSwitchStagnation(double timeProgress, double cumulativeReturn) {
        return timeProgress >= 200 && cumulativeReturn < 0;
    }

    private boolean isZombieRemainder(DsipPartition partition, double daysElapsed) {
        double capitalRemaining = partition.getPartitionCapitalAllocated() - partition.getCapitalInvestedSoFar();
        double minimumTradableAmount = Math.max(1,
                capitalRemaining / (partition.getExpectedPartitionDays() - daysElapsed));
        return capitalRemaining < minimumTradableAmount;
    }

    private boolean isNeutralByTimeExhaustion(double netProfitPct, double timeElapsedRatio) {
        return netProfitPct >= 0 && timeElapsedRatio >= 150;
    }
}