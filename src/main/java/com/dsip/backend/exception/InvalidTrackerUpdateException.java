package com.dsip.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTrackerUpdateException extends DsipException {

    public InvalidTrackerUpdateException(String fieldName, Integer currentValue, Integer requestedValue) {
        super(String.format(
                "%s cannot be decreased. Current: %d, Requested: %d",
                fieldName, currentValue, requestedValue));
    }
}
