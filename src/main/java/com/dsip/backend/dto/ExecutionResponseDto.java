package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Response DTO for trade execution.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionResponseDto {

    /**
     * Execution status: "EXECUTED" or "SKIPPED".
     */
    @JsonProperty("status")
    String status;

    /**
     * Machine-readable code for the end reason.
     */
    @JsonProperty("code")
    String code;

    /**
     * User-friendly title with emoji prefix.
     */
    @JsonProperty("title")
    String title;

    /**
     * Detailed explanation message with metrics.
     */
    @JsonProperty("message")
    String message;

    /**
     * Capital deployed in the partition.
     */
    @JsonProperty("deployed_amount")
    Double deployedAmount;

    /**
     * Net profit percentage.
     */
    @JsonProperty("profit_pct")
    Double profitPct;
}
