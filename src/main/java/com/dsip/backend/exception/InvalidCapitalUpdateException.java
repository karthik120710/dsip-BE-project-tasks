package com.dsip.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCapitalUpdateException extends DsipException {

    public InvalidCapitalUpdateException(Integer currentAmount, Integer requestedAmount) {
        super(String.format(
                "Total capital planned cannot be decreased. Current: %d, Requested: %d",
                currentAmount, requestedAmount));
    }
}
