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

    private Double convictionPeriodYears;
    private Double totalCapitalPlanned;

    private Integer partitionDays;
    private Integer deploymentStyle;
    private Integer baseConvictionScore;

    private Double initialInvestedAmount;
    private Double initialSharesHeld;

    private Integer status;

    private Integer activePartitionIndex;
    private Double totalCapitalInvestedSoFar;
    private Double sharesHeldSoFar;
    private Boolean isFractionalSharesAllowed;

    private Instant createdAt;

    // Transient fields from stock join (not persisted)
    private String stockSymbol;
    private String stockName;
    private Double currentPrice;
}
