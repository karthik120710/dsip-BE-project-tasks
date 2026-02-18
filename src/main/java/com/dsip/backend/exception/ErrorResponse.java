package com.dsip.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @JsonProperty("type")
    private final String type;

    @JsonProperty("code")
    private final String code;

    @JsonProperty("error")
    private final String error;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("timestamp")
    private final String timestamp;

    @JsonProperty("details")
    private final Map<String, String> details;

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .type(errorCode.getType().getValue())
                .code(errorCode.getCode())
                .error(formatErrorLabel(errorCode))
                .message(message)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, String> details) {
        return ErrorResponse.builder()
                .type(errorCode.getType().getValue())
                .code(errorCode.getCode())
                .error(formatErrorLabel(errorCode))
                .message(message)
                .details(details)
                .timestamp(Instant.now().toString())
                .build();
    }

    private static String formatErrorLabel(ErrorCode errorCode) {
        String code = errorCode.getCode();
        return code.substring(0, 1).toUpperCase() +
                code.substring(1).toLowerCase().replace("_", " ");
    }
}
