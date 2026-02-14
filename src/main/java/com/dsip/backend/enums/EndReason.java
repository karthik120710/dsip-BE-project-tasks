package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum EndReason {
    SUCCESS,
    KILL_SWITCH_STAGNATION,
    KILL_SWITCH_POOR_GROWTH,
    KILL_SWITCH_ZOMBIE,
    NEUTRAL_PARTITION;

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static EndReason fromString(String key) {
        return Arrays.stream(values())
                .filter(reason -> reason.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown EndReason: " + key));
    }

    /**
     * Converts this EndReason to the corresponding PartitionStatus for database
     * persistence.
     * 
     * @return the PartitionStatus that should be saved when a partition ends with
     *         this reason
     */
    public PartitionStatus toPartitionStatus() {
        return switch (this) {
            case SUCCESS -> PartitionStatus.COMPLETED;
            case KILL_SWITCH_STAGNATION -> PartitionStatus.KILL_SWITCH_STAGNATION;
            case KILL_SWITCH_POOR_GROWTH -> PartitionStatus.KILL_SWITCH_POOR_GROWTH;
            case KILL_SWITCH_ZOMBIE -> PartitionStatus.KILL_SWITCH_ZOMBIE;
            case NEUTRAL_PARTITION -> PartitionStatus.NEUTRAL;
        };
    }

    public static EndReason fromPartitionStatus(PartitionStatus status) {
        if (status == PartitionStatus.COMPLETED) {
            return SUCCESS;
        }
        if (status == PartitionStatus.KILL_SWITCH_STAGNATION) {
            return KILL_SWITCH_STAGNATION;
        }
        if (status == PartitionStatus.KILL_SWITCH_POOR_GROWTH) {
            return KILL_SWITCH_POOR_GROWTH;
        }
        if (status == PartitionStatus.KILL_SWITCH_ZOMBIE) {
            return KILL_SWITCH_ZOMBIE;
        }
        if (status == PartitionStatus.NEUTRAL) {
            return NEUTRAL_PARTITION;
        }
        return null; // For ACTIVE or other statuses
    }
}
