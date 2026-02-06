package com.dsip.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipExecution {
    private Integer executionId;

    private Integer trackerId;
    private Integer partitionId;

    private Integer lockInPercentage;
    private Integer convictionOverride;

    private Double executedAmount;
    private Double executionPrice;

    private Instant createdAt;
}
