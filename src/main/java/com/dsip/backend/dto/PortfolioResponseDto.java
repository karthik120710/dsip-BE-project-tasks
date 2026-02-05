package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponseDto {

    @JsonProperty("total_market_value")
    private Double totalMarketValue;

    @JsonProperty("total_capital_invested_so_far")
    private Integer totalInvestedCapital;

    @JsonProperty("net_profit_percentage")
    private Double netProfitPercentage;

    @JsonProperty("dsip_total_market_value")
    private Double dsipTotalMarketValue;

    @JsonProperty("dsip_total_capital_invested_so_far")
    private Integer dsipTotalInvestedCapital;

    @JsonProperty("dsip_total_net_profit_percentage")
    private Double dsipTotalNetProfitPercentage;

    @JsonProperty("dsip_trackers")
    private List<TrackerSummaryDto> dsipTrackers;
}
