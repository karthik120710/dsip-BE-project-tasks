package com.dsip.backend.exception;

/**
 * Exception thrown when an execution request violates business rules.
 */
public class InvalidExecutionException extends DsipException {

    public InvalidExecutionException(String message) {
        super(message);
    }

    public InvalidExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
