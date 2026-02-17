package com.dsip.backend.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    VALIDATION_ERROR("validation_error", "Request validation failed"),
    CLIENT_ERROR("client_error", "Client request error"),
    SERVER_ERROR("server_error", "Internal server error");

    private final String value;
    private final String description;
}
