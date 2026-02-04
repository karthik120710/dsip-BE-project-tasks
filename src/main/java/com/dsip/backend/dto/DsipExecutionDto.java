package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipExecutionDto {

    @JsonProperty("execution_id")
    private Integer executionId;

    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("partition_id")
    private Integer partitionId;

    @JsonProperty("lock_in_percentage")
    private Integer lockInPercentage;

    @JsonProperty("conviction_override")
    private Integer convictionOverride;

    @JsonProperty("executed_amount")
    private Integer executedAmount;

    @JsonProperty("execution_price")
    private Integer executionPrice;

    @JsonProperty("created_at")
    private java.time.Instant createdAt;

    public static DsipExecutionDto fromEntity(com.dsip.backend.entity.DsipExecution execution) {
        return DsipExecutionDto.builder()
                .executionId(execution.getExecutionId())
                .trackerId(execution.getTrackerId())
                .partitionId(execution.getPartitionId())
                .lockInPercentage(execution.getLockInPercentage())
                .convictionOverride(execution.getConvictionOverride())
                .executedAmount(execution.getExecutedAmount())
                .executionPrice(execution.getExecutionPrice())
                .createdAt(execution.getCreatedAt())
                .build();
    }
}
