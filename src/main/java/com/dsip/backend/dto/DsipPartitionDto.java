package com.dsip.backend.dto;

import com.dsip.backend.entity.DsipPartition;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @JsonProperty("expected_partition_days")
    private Integer expectedPartitionDays;

    @JsonProperty("partition_capital_allocated")
    private Integer partitionCapitalAllocated;

    @JsonProperty("capital_invested_so_far")
    private Integer capitalInvestedSoFar;

    @JsonProperty("no_of_shares_bought")
    private Integer noOfSharesBought;

    @JsonProperty("successful_growth_count")
    private Integer successfulGrowthCount;

    @JsonProperty("partition_end_date")
    private LocalDate partitionEndDate;

    private Integer status;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static DsipPartitionDto fromEntity(DsipPartition partition) {
        return DsipPartitionDto.builder()
                .partitionId(partition.getPartitionId())
                .trackerId(partition.getTrackerId())
                .partitionIndex(partition.getPartitionIndex())
                .expectedPartitionDays(partition.getExpectedPartitionDays())
                .partitionCapitalAllocated(partition.getPartitionCapitalAllocated())
                .capitalInvestedSoFar(partition.getCapitalInvestedSoFar())
                .noOfSharesBought(partition.getNoOfSharesBought())
                .successfulGrowthCount(partition.getSuccessfulGrowthCount())
                .partitionEndDate(partition.getPartitionEndDate())
                .status(partition.getStatus())
                .createdAt(partition.getCreatedAt())
                .build();
    }
}
