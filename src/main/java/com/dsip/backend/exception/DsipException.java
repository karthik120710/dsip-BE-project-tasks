package com.dsip.backend.exception;

/**
 * Base exception class for all DSIP-related business exceptions.
 * All domain-specific exceptions should extend this class.
 */
public abstract class DsipException extends RuntimeException {

    protected DsipException(String message) {
        super(message);
    }

    protected DsipException(String message, Throwable cause) {
        super(message, cause);
    }
}
