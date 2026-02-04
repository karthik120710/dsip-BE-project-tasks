package com.dsip.backend.entity;

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
public class DsipPartition {
    private Integer partitionId;
    private Integer trackerId;

    private Integer partitionIndex;

    private Integer expectedPartitionDays;
    private Integer partitionCapitalAllocated;

    private Integer capitalInvestedSoFar;
    private Integer noOfSharesBought;
    private Integer successfulGrowthCount;

    private LocalDate partitionEndDate;

    private Integer status;

    private Instant createdAt;
}
