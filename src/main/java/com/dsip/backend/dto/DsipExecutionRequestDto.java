package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipExecutionRequestDto {

    @JsonProperty("lock_in_percentage")
    @NotNull(message = "Lock-in percentage is required")
    @Max(value = 100, message = "Lock-in percentage cannot exceed 100")
    private Double lockInPercentage;

    @JsonProperty("conviction_override")
    @NotNull(message = "Conviction override is required")
    @Min(value = 0, message = "Conviction override cannot be negative")
    @Max(value = 100, message = "Conviction override cannot exceed 100")
    private Integer convictionOverride;

    @JsonProperty("executed_amount")
    @NotNull(message = "Executed amount is required")
    @Min(value = 1, message = "Executed amount must be positive")
    private Double executedAmount;

    @JsonProperty("execution_price")
    @NotNull(message = "Execution price is required")
    @Min(value = 1, message = "Execution price must be positive")
    private Double executionPrice;
}
