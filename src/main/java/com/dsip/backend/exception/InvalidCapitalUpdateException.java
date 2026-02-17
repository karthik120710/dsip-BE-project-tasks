package com.dsip.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCapitalUpdateException extends DsipException {

    public InvalidCapitalUpdateException(Double currentAmount, Double requestedAmount) {
        super(String.format(
                "Total capital planned cannot be decreased. Current: %.2f, Requested: %.2f",
                currentAmount, requestedAmount));
    }
}
