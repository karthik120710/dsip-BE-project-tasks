package com.dsip.backend.exception;

/**
 * Exception thrown when attempting to create a tracker that already exists
 * for a user and stock symbol combination.
 */
public class DuplicateTrackerException extends DsipException {

    public DuplicateTrackerException(String stockSymbol) {
        super("A tracker for stock symbol '" + stockSymbol + "' already exists");
    }

    public DuplicateTrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
