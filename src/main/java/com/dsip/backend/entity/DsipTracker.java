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

    private Integer stockId;

    private Integer convictionPeriodYears;
    private Integer totalCapitalPlanned;

    private Integer partitionDays;
    private Integer deploymentStyle;
    private Integer baseConvictionScore;

    private Integer initialInvestedAmount;
    private Integer initialSharesHeld;

    private Integer status;

    private Integer activePartitionIndex;
    private Integer totalCapitalInvestedSoFar;
    private Integer sharesHeldSoFar;
    private Boolean isFractionalSharesAllowed;

    private Instant createdAt;

    // Transient fields from stock join (not persisted)
    private String stockSymbol;
    private String stockName;
    private Double currentPrice;
}
