package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Response DTO for partition end action.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartitionEndActionResponseDto {

    /**
     * Action taken: "NEXT_PARTITION_CREATED", "TRACKER_COMPLETED", or
     * "ALREADY_PROCESSED".
     */
    @JsonProperty("action")
    String action;

    /**
     * User-friendly message explaining the action taken.
     */
    @JsonProperty("message")
    String message;

    /**
     * The partition index that was processed.
     */
    @JsonProperty("partition_index")
    Integer partitionIndex;

    /**
     * The new active partition index (only if NEXT_PARTITION_CREATED).
     */
    @JsonProperty("new_partition_index")
    Integer newPartitionIndex;

}
