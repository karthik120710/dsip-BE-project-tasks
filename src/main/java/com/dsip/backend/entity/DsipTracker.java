package com.dsip.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipTracker {
    private Integer trackerId;
    private UUID userId;

    private String stockSymbol;

    private Integer convictionPeriodYears;
    private Integer totalCapitalPlanned;

    private Integer partitionDays;
    private Integer deploymentStyle;
    private Integer baseConvictionScore;

    private Integer initialInvestedAmount;
    private Integer initialSharesHeld;

    private Integer status;

    private Integer currentPartitionIndex;

    private Instant createdAt;
    private Instant updatedAt;
}
