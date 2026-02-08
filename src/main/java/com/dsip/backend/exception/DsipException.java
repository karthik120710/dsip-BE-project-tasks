package com.dsip.backend.exception;

import lombok.Getter;

/**
 * Base exception class for all DSIP-related business exceptions.
 * All domain-specific exceptions should extend this class.
 */
@Getter
public abstract class DsipException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DsipException(String message) {
        super(message);
        this.errorCode = null;
    }

    protected DsipException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    protected DsipException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DsipException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
