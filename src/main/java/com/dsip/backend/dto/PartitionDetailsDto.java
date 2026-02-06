package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PartitionDetailsDto {

    @JsonProperty("partition_index")
    private Integer partitionIndex;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("capital_allocated")
    private Double capitalAllocated;

    @JsonProperty("capital_deployed")
    private Double capitalDeployed;

    @JsonProperty("shares_bought")
    private Double sharesBought;

    @JsonProperty("current_market_value")
    private Double currentMarketValue;

    @JsonProperty("net_profit_percentage")
    private Double netProfitPercentage;

    @JsonProperty("growth_count")
    private Integer growthCount;

    @JsonProperty("expected_days")
    private Integer expectedDays;

    @JsonProperty("start_date")
    private Instant startDate;

    @JsonProperty("end_date")
    private Instant endDate;
}
