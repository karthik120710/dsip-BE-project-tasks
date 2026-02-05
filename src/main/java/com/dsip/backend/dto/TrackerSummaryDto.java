package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackerSummaryDto {

    private Integer id;

    private String symbol;

    private String name;

    @JsonProperty("total_capital_invested_so_far")
    private Integer totalCapitalInvestedSoFar;

    @JsonProperty("total_capital_planned")
    private Integer totalCapitalPlanned;

    @JsonProperty("net_profit_percentage")
    private Double netProfitPercentage;

    @JsonProperty("dsip_total_capital_invested_so_far")
    private Integer dsipTotalInvestedCapital;

    @JsonProperty("dsip_net_profit_percentage")
    private Double dsipNetProfitPercentage;
}
