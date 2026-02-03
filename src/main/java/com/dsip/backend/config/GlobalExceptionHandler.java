package com.dsip.backend.config;

import com.dsip.backend.exception.StockNotFoundException;
import com.dsip.backend.exception.StockPriceFetchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        Object target = ex.getBindingResult().getTarget();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            String jsonFieldName = fieldName;

            if (target != null) {
                try {
                    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
                    com.fasterxml.jackson.annotation.JsonProperty annotation = field
                            .getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
                    if (annotation != null) {
                        jsonFieldName = annotation.value();
                    }
                } catch (NoSuchFieldException e) {
                    // unexpected, fallback to fieldName
                }
            }

            errors.put(jsonFieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Validation failed",
                "details", errors,
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(
            MissingServletRequestParameterException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Missing required parameter",
                "message", String.format("Required parameter '%s' of type %s is missing",
                        ex.getParameterName(), ex.getParameterType()),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
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

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Invalid parameter",
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStockNotFound(StockNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Stock not found",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(StockPriceFetchException.class)
    public ResponseEntity<Map<String, Object>> handleStockPriceFetchError(StockPriceFetchException ex) {
        log.error("Stock price fetch failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Unable to fetch stock price",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad request",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatchException(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {

        String message = "Invalid parameter value";
        if (ex.getRequiredType() != null) {
            message = String.format("Invalid value for parameter '%s': expected %s",
                    ex.getName(), ex.getRequiredType().getSimpleName());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad Request",
                "message", message,
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {

        String message = "Invalid JSON request body";

        Throwable cause = ex.getCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException) {
            com.fasterxml.jackson.databind.exc.InvalidFormatException ifx = (com.fasterxml.jackson.databind.exc.InvalidFormatException) cause;

            if (!ifx.getPath().isEmpty()) {
                String fieldName = ifx.getPath().get(ifx.getPath().size() - 1).getFieldName();
                String targetType = ifx.getTargetType().getSimpleName();
                message = String.format("Invalid value for field '%s': expected %s", fieldName, targetType);
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad Request",
                "message", message,
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex) {

        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason() != null ? ex.getReason() : "Error",
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(com.dsip.backend.exception.TrackerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTrackerNotFoundException(
            com.dsip.backend.exception.TrackerNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Tracker not found",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(com.dsip.backend.exception.PartitionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePartitionNotFoundException(
            com.dsip.backend.exception.PartitionNotFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "Partition not found",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(com.dsip.backend.exception.UnauthorizedTrackerAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedTrackerAccess(
            com.dsip.backend.exception.UnauthorizedTrackerAccessException ex) {

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Forbidden",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(com.dsip.backend.exception.DuplicateTrackerException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateTrackerException(
            com.dsip.backend.exception.DuplicateTrackerException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Duplicate tracker",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(com.dsip.backend.exception.InvalidExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidExecutionException(
            com.dsip.backend.exception.InvalidExecutionException ex) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Invalid execution",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(com.dsip.backend.exception.PartitionAlreadyCompletedException.class)
    public ResponseEntity<Map<String, Object>> handlePartitionAlreadyCompletedException(
            com.dsip.backend.exception.PartitionAlreadyCompletedException ex) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Partition already completed",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal server error",
                "message", "An unexpected error occurred",
                "timestamp", Instant.now().toString()));
    }
}
