package com.dsip.backend.dto;

import com.dsip.backend.enums.EndReason;
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
     * Execution status: always "EXECUTED" when successful.
     */
    @JsonProperty("status")
    String status;

    /**
     * The reason the partition ended (if it ended).
     * Possible values: SUCCESS, KILL_SWITCH, NEUTRAL_PARTITION, or null if
     * partition is still active.
     */
    @JsonProperty("end_reason")
    EndReason endReason;
}
