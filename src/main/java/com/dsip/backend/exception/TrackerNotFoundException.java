package com.dsip.backend.exception;

/**
 * Exception thrown when a DSIP tracker is not found.
 */
public class TrackerNotFoundException extends DsipException {

    public TrackerNotFoundException(Integer trackerId) {
        super("Tracker not found with ID: " + trackerId);
    }

    public TrackerNotFoundException(String message) {
        super(message);
    }
}
