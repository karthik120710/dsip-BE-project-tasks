package com.dsip.backend.config;

import com.dsip.backend.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ========== VALIDATION ERRORS ==========
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        Object target = ex.getBindingResult().getTarget();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            String jsonFieldName = resolveJsonFieldName(target, fieldName);
            fieldErrors.put(jsonFieldName, errorMessage);
        });

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_FAILED,
                "One or more fields failed validation",
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex) {

        String message = String.format("Required parameter '%s' of type %s is missing",
                ex.getParameterName(), ex.getParameterType());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.MISSING_PARAMETER, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message;
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            Object[] enumConstants = ex.getRequiredType().getEnumConstants();
            message = String.format("Invalid value '%s' for parameter '%s'. Allowed values: %s",
                    ex.getValue(), ex.getName(), Arrays.toString(enumConstants));
        } else {
            message = String.format("Invalid value '%s' for parameter '%s'",
                    ex.getValue(), ex.getName());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_PARAMETER, message));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {

        String message = "Invalid JSON request body";
        Throwable cause = ex.getCause();

        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException) {
            var ifx = (com.fasterxml.jackson.databind.exc.InvalidFormatException) cause;
            if (!ifx.getPath().isEmpty()) {
                String fieldName = ifx.getPath().get(ifx.getPath().size() - 1).getFieldName();
                String targetType = ifx.getTargetType().getSimpleName();
                message = String.format("Invalid value for field '%s': expected %s",
                        fieldName, targetType);
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_JSON, message));
    }

    // ========== DSIP DOMAIN EXCEPTIONS (Unified Handler) ==========
    @ExceptionHandler(DsipException.class)
    public ResponseEntity<ErrorResponse> handleDsipException(DsipException ex) {
        ErrorCode errorCode = resolveErrorCode(ex);

        if (errorCode.getType() == ErrorType.SERVER_ERROR) {
            log.error("Server error: {} - {}", errorCode.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("Domain exception: {} - {}", errorCode.getCode(), ex.getMessage());
        }

        ErrorResponse response = ErrorResponse.of(errorCode, ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    // ========== FRAMEWORK EXCEPTIONS ==========
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_ARGUMENT, ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex) {

        ErrorCode errorCode = mapHttpStatusToErrorCode(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : "An error occurred";

        return ResponseEntity.status(ex.getStatusCode())
                .body(ErrorResponse.of(errorCode, message));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage()));
    }

    // ========== CATCH-ALL HANDLER ==========
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception of type {}: {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        String message = determineUserMessage(ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, message));
    }

    // ========== HELPER METHODS ==========
    private ErrorCode resolveErrorCode(DsipException ex) {
        if (ex.getErrorCode() != null) {
            return ex.getErrorCode();
        }

        if (ex instanceof TrackerNotFoundException) {
            return ErrorCode.TRACKER_NOT_FOUND;
        } else if (ex instanceof PartitionNotFoundException) {
            return ErrorCode.PARTITION_NOT_FOUND;
        } else if (ex instanceof StockNotFoundException) {
            return ErrorCode.STOCK_NOT_FOUND;
        } else if (ex instanceof UnauthorizedTrackerAccessException) {
            return ErrorCode.UNAUTHORIZED_ACCESS;
        } else if (ex instanceof DuplicateTrackerException) {
            return ErrorCode.DUPLICATE_TRACKER;
        } else if (ex instanceof PartitionAlreadyCompletedException) {
            return ErrorCode.PARTITION_ALREADY_COMPLETED;
        } else if (ex instanceof InvalidExecutionException) {
            return ErrorCode.INVALID_EXECUTION;
        } else if (ex instanceof InvalidTrackerUpdateException) {
            return ErrorCode.INVALID_TRACKER_UPDATE;
        } else if (ex instanceof InvalidCapitalUpdateException) {
            return ErrorCode.INVALID_CAPITAL_UPDATE;
        } else if (ex instanceof StockPriceFetchException) {
            return ErrorCode.STOCK_PRICE_FETCH_FAILED;
        }

        return ErrorCode.INTERNAL_ERROR;
    }

    private ErrorCode mapHttpStatusToErrorCode(int statusCode) {
        if (statusCode == 404) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (statusCode == 403) {
            return ErrorCode.UNAUTHORIZED_ACCESS;
        }
        if (statusCode == 400) {
            return ErrorCode.INVALID_ARGUMENT;
        }
        if (statusCode == 409) {
            return ErrorCode.DUPLICATE_TRACKER;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private String determineUserMessage(Exception ex) {
        String className = ex.getClass().getSimpleName();

        if (className.contains("DataAccess") || className.contains("Database") || className.contains("SQL")) {
            return "A database error occurred. Please try again later.";
        }
        if (className.contains("Timeout") || className.contains("Connection")) {
            return "The service is temporarily unavailable. Please try again.";
        }
        if (className.contains("NullPointer")) {
            return "An internal processing error occurred.";
        }

        String message = ex.getMessage();
        if (message != null && message.length() < 200 && !message.contains("Exception")) {
            return message;
        }

        return "An error occurred while processing your request.";
    }

    private String resolveJsonFieldName(Object target, String fieldName) {
        if (target == null) {
            return fieldName;
        }

        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            com.fasterxml.jackson.annotation.JsonProperty annotation
                    = field.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
            if (annotation != null) {
                return annotation.value();
            }
        } catch (NoSuchFieldException e) {
            // Fallback to fieldName
        }
        return fieldName;
    }
}
