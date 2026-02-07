package com.dsip.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTrackerUpdateException extends DsipException {

    public InvalidTrackerUpdateException(String fieldName, Double currentValue, Double requestedValue) {
        super(String.format(
                "%s cannot be decreased. Current: %.2f, Requested: %.2f",
                fieldName, currentValue, requestedValue));
    }
}
