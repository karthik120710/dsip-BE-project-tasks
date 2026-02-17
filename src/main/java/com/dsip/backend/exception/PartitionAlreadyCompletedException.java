package com.dsip.backend.exception;

/**
 * Exception thrown when attempting to execute on an already completed
 * partition.
 */
public class PartitionAlreadyCompletedException extends DsipException {

    public PartitionAlreadyCompletedException(Integer partitionId) {
        super("Partition " + partitionId + " is already completed");
    }

    public PartitionAlreadyCompletedException(String message) {
        super(message);
    }
}
