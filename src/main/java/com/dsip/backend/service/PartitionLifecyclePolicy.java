package com.dsip.backend.service;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.enums.EndReason;
import com.dsip.backend.model.PartitionEndDecision;
import com.dsip.backend.util.FinancialCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Evaluates partition lifecycle conditions to determine if a partition should end.
 *
 * Decision order (evaluated in sequence, first match wins):
 * 1. SUCCESS: partitionProgress >= 80% AND (timeProgress >= 100% OR capitalProgress >= 90%)
 * 2. KILL (severe loss): cumulativeReturn <= -30%
 * 3. KILL (time exhaustion): cumulativeReturn < 0 AND timeProgress >= 200%
 * 4. ZOMBIE_REMAINDER: capital remaining is too small for meaningful trades
 * 5. NEUTRAL: cumulativeReturn >= 0 AND timeProgress >= 150%
 * 6. CONTINUE: no end conditions met
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartitionLifecyclePolicy {

    private final FinancialCalculator financialCalculator;
    private final DsipProperties dsipProperties;

    public PartitionEndDecision evaluate(DsipTracker tracker, DsipPartition partition, double marketPrice) {
        // Calculate all metrics once to avoid redundant calculations
        double partitionProgress = financialCalculator.calculatePartitionProgressPercentage(partition, marketPrice);
        double timeProgress = financialCalculator.calculateTimeProgressPercentage(partition);
        double capitalProgress = financialCalculator.calculateCapitalProgressPercentage(partition);
        double cumulativeReturn = financialCalculator.cumulativeReturnPercentage(
                partition.getNoOfSharesBought(),
                partition.getCapitalInvestedSoFar(),
                marketPrice);
        double daysElapsed = financialCalculator.calculateDaysBetween(partition.getCreatedAt(), Instant.now());

        log.debug("Lifecycle evaluation - partitionProgress: {}%, timeProgress: {}%, capitalProgress: {}%, " +
                        "cumulativeReturn: {}%, daysElapsed: {}",
                partitionProgress, timeProgress, capitalProgress, cumulativeReturn, daysElapsed);

        // 1. SUCCESS - Target achieved
        if (isSuccessCondition(partitionProgress, timeProgress, capitalProgress)) {
            log.info("Partition {} SUCCESS: progress={}%, time={}%, capital={}%",
                    partition.getPartitionIndex(), partitionProgress, timeProgress, capitalProgress);
            return PartitionEndDecision.end(EndReason.SUCCESS);
        }

        // 2. KILL - Severe loss (hard -30% stop) - NEW CONDITION
        if (isKillSwitchSevereLoss(cumulativeReturn)) {
            log.warn("Partition {} KILL (severe loss): cumulativeReturn={}% <= {}%",
                    partition.getPartitionIndex(), cumulativeReturn, dsipProperties.getKillReturnThreshold());
            return PartitionEndDecision.end(EndReason.KILL_SWITCH);
        }

        // 3. KILL - Time exhaustion with negative return
        if (isKillSwitchTimeExhaustion(timeProgress, cumulativeReturn)) {
            log.warn("Partition {} KILL (time exhaustion): timeProgress={}% >= 200% with negative return={}%",
                    partition.getPartitionIndex(), timeProgress, cumulativeReturn);
            return PartitionEndDecision.end(EndReason.KILL_SWITCH);
        }

        // 4. ZOMBIE_REMAINDER - Capital too small for meaningful trades
        if (isZombieRemainder(partition, daysElapsed)) {
            if (cumulativeReturn >= 0) {
                log.info("Partition {} NEUTRAL (zombie remainder with non-negative return): return={}%",
                        partition.getPartitionIndex(), cumulativeReturn);
                return PartitionEndDecision.end(EndReason.NEUTRAL_PARTITION);
            } else {
                log.warn("Partition {} KILL (zombie remainder with negative return): return={}%",
                        partition.getPartitionIndex(), cumulativeReturn);
                return PartitionEndDecision.end(EndReason.KILL_SWITCH);
            }
        }

        // 5. NEUTRAL - Time exhaustion but non-negative return
        if (isNeutralByTimeExhaustion(cumulativeReturn, timeProgress)) {
            log.info("Partition {} NEUTRAL (time exhaustion): timeProgress={}% >= 150% with return={}%",
                    partition.getPartitionIndex(), timeProgress, cumulativeReturn);
            return PartitionEndDecision.end(EndReason.NEUTRAL_PARTITION);
        }

        // 6. CONTINUE - No end conditions met
        return PartitionEndDecision.continueRunning();
    }

    /**
     * SUCCESS: partitionProgress >= 80% AND (timeProgress >= 100% OR capitalProgress >= 90%)
     */
    private boolean isSuccessCondition(double partitionProgress, double timeProgress, double capitalProgress) {
        double progressThreshold = dsipProperties.getSuccessProgressThreshold() * 100; // Convert to percentage
        double timeThreshold = dsipProperties.getSuccessTimeThreshold() * 100;
        double capitalThreshold = dsipProperties.getSuccessCapitalThreshold() * 100;

        return partitionProgress >= progressThreshold
                && (timeProgress >= timeThreshold || capitalProgress >= capitalThreshold);
    }

    /**
     * KILL (severe loss): cumulativeReturn <= -30%
     * This is a hard stop to prevent catastrophic losses.
     */
    private boolean isKillSwitchSevereLoss(double cumulativeReturn) {
        return cumulativeReturn <= dsipProperties.getKillReturnThreshold();
    }

    /**
     * KILL (time exhaustion): timeProgress >= 200% AND cumulativeReturn < 0
     * Partition has run twice its expected length with negative returns.
     */
    private boolean isKillSwitchTimeExhaustion(double timeProgress, double cumulativeReturn) {
        double timeThreshold = dsipProperties.getKillTimeThreshold() * 100; // Convert to percentage
        return timeProgress >= timeThreshold && cumulativeReturn < 0;
    }

    /**
     * ZOMBIE_REMAINDER: Remaining capital is too small for meaningful trades.
     * Evaluated after kill conditions to avoid premature zombie detection.
     */
    private boolean isZombieRemainder(DsipPartition partition, double daysElapsed) {
        double capitalRemaining = partition.getPartitionCapitalAllocated() - partition.getCapitalInvestedSoFar();
        double daysRemaining = partition.getExpectedPartitionDays() - daysElapsed;

        if (daysRemaining <= 0) {
            return capitalRemaining > 0; // Time exhausted but capital remains
        }

        double minimumTradableAmount = Math.max(1, capitalRemaining / daysRemaining);
        return capitalRemaining < minimumTradableAmount;
    }

    /**
     * NEUTRAL: cumulativeReturn >= 0 AND timeProgress >= 150%
     * Partition has run 1.5x its expected length but is still profitable.
     */
    private boolean isNeutralByTimeExhaustion(double cumulativeReturn, double timeProgress) {
        double timeThreshold = dsipProperties.getNeutralTimeThreshold() * 100; // Convert to percentage
        return cumulativeReturn >= 0 && timeProgress >= timeThreshold;
    }
}
