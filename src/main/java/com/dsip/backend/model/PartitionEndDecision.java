package com.dsip.backend.model;

import com.dsip.backend.enums.EndReason;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PartitionEndDecision {
    boolean shouldEnd;
    EndReason reason;

    public static PartitionEndDecision continueRunning() {
        return new PartitionEndDecision(false, null);
    }

    public static PartitionEndDecision end(EndReason reason) {
        return new PartitionEndDecision(true, reason);
    }
}
