package com.dsip.backend.exception;

/**
 * Exception thrown when a DSIP partition is not found.
 */
public class PartitionNotFoundException extends DsipException {

    public PartitionNotFoundException(Integer trackerId) {
        super("No active partition found for tracker ID: " + trackerId);
    }

    public PartitionNotFoundException(String message) {
        super(message);
    }
}
