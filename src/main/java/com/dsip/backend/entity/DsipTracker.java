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
    private Double currentPrice; // last_date_market_closing_price (prev day's close)
    private Integer stockType; // StockType enum value (1=PENNY, 2=MIDCAP, 3=LARGECAP, 4=ETF)
}
