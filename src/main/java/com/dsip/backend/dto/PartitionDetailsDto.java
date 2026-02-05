package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PartitionDetailsDto {

    @JsonProperty("capital_deployed")
    private Double capitalDeployed;

    @JsonProperty("net_profit_percentage")
    private Double netProfitPercentage;

    @JsonProperty("start_date")
    private Instant startDate;

    @JsonProperty("end_date")
    private Instant endDate;
}
