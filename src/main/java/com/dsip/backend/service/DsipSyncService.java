package com.dsip.backend.service;

import com.dsip.backend.dto.SyncRequest;
import com.dsip.backend.dto.SyncResponse;
import com.dsip.backend.entity.DsipPartition;
import com.dsip.backend.entity.DsipTracker;
import com.dsip.backend.entity.User;
import com.dsip.backend.enums.PartitionStatus;
import com.dsip.backend.exception.ErrorCode;
import com.dsip.backend.exception.PartitionNotFoundException;
import com.dsip.backend.exception.SyncException;
import com.dsip.backend.exception.TrackerNotFoundException;
import com.dsip.backend.exception.UnauthorizedTrackerAccessException;
import com.dsip.backend.mapper.DsipTrackerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DsipSyncService {

    private static final double BUY_TOLERANCE_RATIO = 0.25;
    private static final double SELL_TOLERANCE_RATIO = 0.50;

    private final DsipTrackerMapper dsipTrackerMapper;

    @Transactional
    public SyncResponse sync(SyncRequest request, User user) {
        Integer trackerId = request.getTrackerId();

        // Step 1: Load tracker
        DsipTracker tracker = dsipTrackerMapper.findTrackerById(trackerId)
                .orElseThrow(() -> new TrackerNotFoundException(trackerId));

        // Verify ownership
        if (!tracker.getUserId().equals(user.getId())) {
            throw new UnauthorizedTrackerAccessException(trackerId, user.getId());
        }

        // Step 1: Load active partition
        DsipPartition partition = dsipTrackerMapper
                .findPartitionByTrackerIdAndIndex(trackerId, tracker.getActivePartitionIndex())
                .orElseThrow(() -> new PartitionNotFoundException(trackerId));

        // Verify partition is active
        if (partition.getStatus() != PartitionStatus.ACTIVE.getValue()) {
            throw new SyncException("Cannot sync: partition is not active");
        }

        // Step 2: Compute expected totals
        double initialShares = safeDouble(tracker.getInitialSharesHeld());
        double initialInvested = safeDouble(tracker.getInitialInvestedAmount());
        double dsipShares = safeDouble(tracker.getSharesHeldSoFar());
        double dsipCapital = safeDouble(tracker.getTotalCapitalInvestedSoFar());

        double expectedShares = initialShares + dsipShares;
        double expectedCapital = initialInvested + dsipCapital;

        // Step 3: Compute deltas
        double deltaShares = request.getCurrentTotalShares() - expectedShares;
        double deltaCapital = request.getCurrentTotalInvestedAmount() - expectedCapital;

        log.info("Sync request for tracker {}: expectedShares={}, userShares={}, deltaShares={}",
                trackerId, expectedShares, request.getCurrentTotalShares(), deltaShares);
        log.info("Sync request for tracker {}: expectedCapital={}, userCapital={}, deltaCapital={}",
                trackerId, expectedCapital, request.getCurrentTotalInvestedAmount(), deltaCapital);

        // Step 4: Branch based on delta

        // CASE 1: User bought more (missed executions)
        if (deltaShares >= 0 && deltaCapital >= 0) {
            return handleBuySync(tracker, partition, deltaShares, deltaCapital, request.getReason());
        }

        // CASE 2: User sold DSIP shares
        if (deltaShares < 0 && Math.abs(deltaShares) <= dsipShares) {
            return handleSellSync(tracker, partition, Math.abs(deltaShares), Math.abs(deltaCapital), request.getReason());
        }

        // CASE 3: Initial holdings affected → BLOCK
        if (Math.abs(deltaShares) > dsipShares) {
            throw new SyncException(ErrorCode.SYNC_INITIAL_HOLDINGS_AFFECTED,
                    "Sell affects initial holdings. Manual intervention required. " +
                    "Delta shares: " + Math.abs(deltaShares) + ", DSIP shares: " + dsipShares);
        }

        throw new SyncException("Invalid sync state");
    }

    private SyncResponse handleBuySync(DsipTracker tracker, DsipPartition partition,
                                        double deltaShares, double deltaCapital, String reason) {
        // Calculate dynamic buy tolerance: 25% of partition allocated capital
        double partitionCapital = safeDouble(partition.getPartitionCapitalAllocated());
        double buyTolerance = partitionCapital * BUY_TOLERANCE_RATIO;

        if (deltaCapital > buyTolerance) {
            throw new SyncException(ErrorCode.SYNC_BUY_EXCEEDS_TOLERANCE,
                    String.format("Sync buy amount (%.2f) exceeds allowed tolerance (%.2f). " +
                            "Maximum allowed: %.2f%% of partition capital.",
                            deltaCapital, buyTolerance, BUY_TOLERANCE_RATIO * 100));
        }

        // Apply DSIP buy
        applyDsipBuy(tracker, partition, deltaShares, deltaCapital);

        log.info("Sync buy applied for tracker {}: +{} shares, +{} capital. Reason: {}",
                tracker.getTrackerId(), deltaShares, deltaCapital, reason);

        return buildSuccessResponse(tracker);
    }

    private SyncResponse handleSellSync(DsipTracker tracker, DsipPartition partition,
                                         double deltaShares, double deltaCapital, String reason) {
        // Calculate dynamic sell tolerance: 50% of current DSIP capital deployed
        double dsipCapital = safeDouble(tracker.getTotalCapitalInvestedSoFar());
        double sellTolerance = dsipCapital * SELL_TOLERANCE_RATIO;

        if (deltaCapital > sellTolerance) {
            throw new SyncException(ErrorCode.SYNC_SELL_EXCEEDS_TOLERANCE,
                    String.format("Sync sell amount (%.2f) exceeds allowed tolerance (%.2f). " +
                            "Maximum allowed: %.2f%% of DSIP capital deployed.",
                            deltaCapital, sellTolerance, SELL_TOLERANCE_RATIO * 100));
        }

        // Apply DSIP sell
        applyDsipSell(tracker, partition, deltaShares, deltaCapital);

        log.info("Sync sell applied for tracker {}: -{} shares, -{} capital. Reason: {}",
                tracker.getTrackerId(), deltaShares, deltaCapital, reason);

        return buildSuccessResponse(tracker);
    }

    private void applyDsipBuy(DsipTracker tracker, DsipPartition partition,
                               double shares, double capital) {
        // Update tracker
        tracker.setSharesHeldSoFar(safeDouble(tracker.getSharesHeldSoFar()) + shares);
        tracker.setTotalCapitalInvestedSoFar(safeDouble(tracker.getTotalCapitalInvestedSoFar()) + capital);

        // Update partition
        partition.setNoOfSharesBought(safeDouble(partition.getNoOfSharesBought()) + shares);
        partition.setCapitalInvestedSoFar(safeDouble(partition.getCapitalInvestedSoFar()) + capital);

        // Persist changes
        dsipTrackerMapper.updateTracker(tracker);
        dsipTrackerMapper.updatePartition(partition);
    }

    private void applyDsipSell(DsipTracker tracker, DsipPartition partition,
                                double shares, double capital) {
        // Update tracker
        tracker.setSharesHeldSoFar(safeDouble(tracker.getSharesHeldSoFar()) - shares);
        tracker.setTotalCapitalInvestedSoFar(safeDouble(tracker.getTotalCapitalInvestedSoFar()) - capital);

        // Update partition
        partition.setNoOfSharesBought(safeDouble(partition.getNoOfSharesBought()) - shares);
        partition.setCapitalInvestedSoFar(safeDouble(partition.getCapitalInvestedSoFar()) - capital);

        // Persist changes
        dsipTrackerMapper.updateTracker(tracker);
        dsipTrackerMapper.updatePartition(partition);
    }

    private SyncResponse buildSuccessResponse(DsipTracker tracker) {
        double dsipShares = safeDouble(tracker.getSharesHeldSoFar());
        double dsipCapital = safeDouble(tracker.getTotalCapitalInvestedSoFar());
        double initialShares = safeDouble(tracker.getInitialSharesHeld());
        double initialCapital = safeDouble(tracker.getInitialInvestedAmount());

        return SyncResponse.success(
                "Sync successful",
                dsipShares,
                dsipCapital,
                initialShares + dsipShares,
                initialCapital + dsipCapital
        );
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
