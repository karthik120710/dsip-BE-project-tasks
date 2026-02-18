package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Response DTO for investment recommendation endpoint.
 * Contains the recommended daily investment amount and breakdown of calculation signals.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecommendationResponseDto {

    @JsonProperty("tracker_id")
    Integer trackerId;

    @JsonProperty("recommended_amount")
    Double recommendedAmount;

    @JsonProperty("breakdown")
    Breakdown breakdown;

    @JsonProperty("signals")
    Signals signals;

    @JsonProperty("partition_status")
    PartitionStatusInfo partitionStatus;

    @Value
    @Builder
    public static class Breakdown {
        @JsonProperty("neutral_capital")
        Double neutralCapital;

        @JsonProperty("opportunity_multiplier")
        Double opportunityMultiplier;

        @JsonProperty("contingency_multiplier")
        Double contingencyMultiplier;

        @JsonProperty("final_multiplier")
        Double finalMultiplier;
    }

    @Value
    @Builder
    public static class Signals {
        @JsonProperty("avg_holding_price")
        Double avgHoldingPrice;

        @JsonProperty("avg_deviation_pct")
        Double avgDeviationPct;

        @JsonProperty("avg_signal")
        Double avgSignal;

        @JsonProperty("lock_in_pct")
        Double lockInPct;

        @JsonProperty("lock_in_signal")
        Double lockInSignal;

        @JsonProperty("raw_opportunity_signal")
        Double rawOpportunitySignal;

        @JsonProperty("conviction_amplifier")
        Double convictionAmplifier;

        @JsonProperty("is_abnormal_dip")
        Boolean isAbnormalDip;
    }

    @Value
    @Builder
    public static class PartitionStatusInfo {
        @JsonProperty("partition_index")
        Integer partitionIndex;

        @JsonProperty("partition_progress_pct")
        Double partitionProgressPct;

        @JsonProperty("return_progress_pct")
        Double returnProgressPct;

        @JsonProperty("growth_persistence_pct")
        Double growthPersistencePct;

        @JsonProperty("time_progress_pct")
        Double timeProgressPct;

        @JsonProperty("capital_progress_pct")
        Double capitalProgressPct;

        @JsonProperty("capital_deployed")
        Double capitalDeployed;

        @JsonProperty("capital_remaining")
        Double capitalRemaining;

        @JsonProperty("cumulative_return_pct")
        Double cumulativeReturnPct;
    }
}
