package com.dsip.backend.exception;

public class SyncException extends DsipException {

    public SyncException(String message) {
        super(ErrorCode.SYNC_FAILED, message);
    }

    public SyncException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
