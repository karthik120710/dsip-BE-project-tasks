package com.dsip.backend.entity;

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
public class DsipPartition {
    private Integer partitionId;
    private Integer trackerId;

    private Integer partitionIndex;

    private LocalDate partitionStartDate;

    private Integer partitionDays;
    private Integer partitionCapitalAllocated;

    private Integer successfulExecutionsCompleted;
    private Integer capitalDeployedSoFar;

    private Integer activeConvictionScore;

    private BigDecimal totalLockinPercentageCount;
    private Integer consistentGrowthCount;

    private Integer status;

    private LocalDate partitionEndDate;

    private Instant createdAt;
    private Instant updatedAt;
}
