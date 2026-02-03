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
public class DsipExecution {
    private Integer executionId;

    private Integer trackerId;
    private Integer partitionId;

    private LocalDate executionDate;

    private Integer lockInPercentage; // SMALLINT covers -32k to 32k, Integer in Java is fine
    private Integer convictionOverride; // SMALLINT

    private Integer executedAmount; // SMALLINT requested by user
    private BigDecimal executionPrice;

    private BigDecimal lastExecutedAvgPrice;

    private Instant createdAt;
}
