package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackerDetailsDto {

    private Integer id;
    private String symbol;
    private String name;

    @JsonProperty("conviction_period_years")
    private Integer convictionPeriodYears;

    @JsonProperty("total_capital_planned")
    private Integer totalCapitalPlanned;

    @JsonProperty("partition_days")
    private Integer partitionDays;

    @JsonProperty("deployment_style")
    private String deploymentStyle;

    @JsonProperty("base_conviction_score")
    private Integer baseConvictionScore;

    @JsonProperty("status")
    private Integer status;

    // Overall Performance (Includes Initials)
    @JsonProperty("total_capital_invested_so_far")
    private Double totalCapitalInvestedSoFar;

    @JsonProperty("current_total_value")
    private Double currentTotalValue;

    @JsonProperty("net_profit_percentage")
    private Double netProfitPercentage;

    // DSIP Specific Performance (Excludes Initials)
    @JsonProperty("dsip_total_market_value")
    private Double dsipTotalMarketValue;

    @JsonProperty("dsip_total_capital_invested_so_far")
    private Double dsipTotalInvestedCapitalSoFar;

    @JsonProperty("dsip_net_profit_percentage")
    private Double dsipNetProfitPercentage;

    @JsonProperty("active_partition_index")
    private Integer activePartitionIndex;

    @JsonProperty("total_cycles")
    private Integer totalCycles;

    private List<HistoryItem> history;

    @JsonProperty("live_investment_cycle")
    private LiveInvestmentCycle liveInvestmentCycle;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private Instant date;

        @JsonProperty("executed_amount")
        private Integer executedAmount;

        @JsonProperty("executed_price")
        private Integer executedPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveInvestmentCycle {
        @JsonProperty("total_capital_invested_so_far")
        private Integer totalCapitalInvestedSoFar;

        @JsonProperty("partition_progress")
        private Double partitionProgress;

        @JsonProperty("net_profit_percentage")
        private Double netProfitPercentage;
    }
}
