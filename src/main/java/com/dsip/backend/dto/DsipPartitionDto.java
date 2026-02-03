package com.dsip.backend.dto;

import com.dsip.backend.entity.DsipPartition;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipPartitionDto {

    @JsonProperty("partition_id")
    private Integer partitionId;

    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("partition_index")
    private Integer partitionIndex;

    @JsonProperty("partition_start_date")
    private LocalDate partitionStartDate;

    @JsonProperty("partition_days")
    private Integer partitionDays;

    @JsonProperty("partition_capital_allocated")
    private Integer partitionCapitalAllocated;

    @JsonProperty("successful_executions_completed")
    private Integer successfulExecutionsCompleted;

    @JsonProperty("capital_deployed_so_far")
    private Integer capitalDeployedSoFar;

    @JsonProperty("active_conviction_score")
    private Integer activeConvictionScore;

    @JsonProperty("total_lockin_percentage_count")
    private BigDecimal totalLockinPercentageCount;

    @JsonProperty("consistent_growth_count")
    private Integer consistentGrowthCount;

    private Integer status;

    @JsonProperty("partition_end_date")
    private LocalDate partitionEndDate;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public static DsipPartitionDto fromEntity(DsipPartition partition) {
        return DsipPartitionDto.builder()
                .partitionId(partition.getPartitionId())
                .trackerId(partition.getTrackerId())
                .partitionIndex(partition.getPartitionIndex())
                .partitionStartDate(partition.getPartitionStartDate())
                .partitionDays(partition.getPartitionDays())
                .partitionCapitalAllocated(partition.getPartitionCapitalAllocated())
                .successfulExecutionsCompleted(partition.getSuccessfulExecutionsCompleted())
                .capitalDeployedSoFar(partition.getCapitalDeployedSoFar())
                .activeConvictionScore(partition.getActiveConvictionScore())
                .totalLockinPercentageCount(partition.getTotalLockinPercentageCount())
                .consistentGrowthCount(partition.getConsistentGrowthCount())
                .status(partition.getStatus())
                .partitionEndDate(partition.getPartitionEndDate())
                .createdAt(partition.getCreatedAt())
                .updatedAt(partition.getUpdatedAt())
                .build();
    }
}
