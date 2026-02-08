package com.dsip.backend.enums;

public enum EndReason {
    SUCCESS,
    KILL_SWITCH,
    NEUTRAL_PARTITION;

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
}
