package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipExecutionRequestDto {

    @JsonProperty("execution_date")
    @NotNull(message = "Execution date is required")
    @PastOrPresent(message = "Execution date cannot be in the future")
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-M-d")
    private LocalDate executionDate;

    @JsonProperty("lock_in_percentage")
    @NotNull(message = "Lock-in percentage is required")
    @jakarta.validation.constraints.Max(value = 100, message = "Lock-in percentage cannot exceed 100")
    private Integer lockInPercentage;

    @JsonProperty("conviction_override")
    @NotNull(message = "Conviction override is required")
    @jakarta.validation.constraints.Min(value = 0, message = "Conviction override cannot be negative")
    @jakarta.validation.constraints.Max(value = 100, message = "Conviction override cannot exceed 100")
    private Integer convictionOverride;

    @JsonProperty("executed_amount")
    @NotNull(message = "Executed amount is required")
    @jakarta.validation.constraints.Min(value = 1, message = "Executed amount must be at least 1")
    private Integer executedAmount;

    @JsonProperty("execution_price")
    @NotNull(message = "Execution price is required")
    @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "Execution price must be greater than 0")
    private BigDecimal executionPrice;
}
