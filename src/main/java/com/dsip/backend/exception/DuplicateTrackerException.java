package com.dsip.backend.exception;

public class DuplicateTrackerException extends DsipException {

    public DuplicateTrackerException(Integer stockId) {
        super("A tracker for stock ID '" + stockId + "' already exists");
    }

    public DuplicateTrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
