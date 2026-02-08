package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum EndReason {
    SUCCESS,
    KILL_SWITCH,
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
            case KILL_SWITCH -> PartitionStatus.KILL_SWITCH;
            case NEUTRAL_PARTITION -> PartitionStatus.NEUTRAL;
        };
    }

    public static EndReason fromPartitionStatus(PartitionStatus status) {
        if (status == PartitionStatus.COMPLETED) {
            return SUCCESS;
        }
        if (status == PartitionStatus.KILL_SWITCH) {
            return KILL_SWITCH;
        }
        if (status == PartitionStatus.NEUTRAL) {
            return NEUTRAL_PARTITION;
        }
        return null; // For ACTIVE or other statuses
    }
}
