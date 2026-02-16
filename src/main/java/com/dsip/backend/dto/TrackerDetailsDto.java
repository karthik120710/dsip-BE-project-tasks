package com.dsip.backend.dto;

import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.TrackerStatus;
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
    private Double convictionPeriodYears;

    @JsonProperty("total_capital_planned")
    private Double totalCapitalPlanned;

    @JsonProperty("partition_months")
    private Integer partitionMonths;

    @JsonProperty("deployment_style")
    private DeploymentStyle deploymentStyle;

    @JsonProperty("base_conviction_score")
    private Integer baseConvictionScore;

    @JsonProperty("status")
    private TrackerStatus status;

    // Overall Performance (Includes Initials)
    @JsonProperty("total_capital_invested_so_far")
    private Double totalCapitalInvestedSoFar;

    @JsonProperty("total_market_value")
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
        private Double executedAmount;

        @JsonProperty("executed_price")
        private Double executedPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiveInvestmentCycle {
        @JsonProperty("total_capital_invested_so_far")
        private Double totalCapitalInvestedSoFar;

        @JsonProperty("partition_progress")
        private Double partitionProgress;

        @JsonProperty("net_profit_percentage")
        private Double netProfitPercentage;
    }
}
