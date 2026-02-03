package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DsipExecutionDto {

    @JsonProperty("execution_id")
    private Integer executionId;

    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("partition_id")
    private Integer partitionId;

    @JsonProperty("execution_date")
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-M-d")
    private LocalDate executionDate;

    @JsonProperty("lock_in_percentage")
    private Integer lockInPercentage;

    @JsonProperty("conviction_override")
    private Integer convictionOverride;

    @JsonProperty("executed_amount")
    private Integer executedAmount;

    @JsonProperty("execution_price")
    private BigDecimal executionPrice;

    @JsonProperty("last_executed_avg_price")
    private BigDecimal lastExecutedAvgPrice;

    @JsonProperty("created_at")
    private java.time.Instant createdAt;

    public static DsipExecutionDto fromEntity(com.dsip.backend.entity.DsipExecution execution) {
        return DsipExecutionDto.builder()
                .executionId(execution.getExecutionId())
                .trackerId(execution.getTrackerId())
                .partitionId(execution.getPartitionId())
                .executionDate(execution.getExecutionDate())
                .lockInPercentage(execution.getLockInPercentage())
                .convictionOverride(execution.getConvictionOverride())
                .executedAmount(execution.getExecutedAmount())
                .executionPrice(execution.getExecutionPrice())
                .lastExecutedAvgPrice(execution.getLastExecutedAvgPrice())
                .createdAt(execution.getCreatedAt())
                .build();
    }
}
