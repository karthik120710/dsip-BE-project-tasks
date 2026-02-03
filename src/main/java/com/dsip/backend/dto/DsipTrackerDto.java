package com.dsip.backend.dto;

import com.dsip.backend.validation.ValidEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipTrackerDto {

    private Integer trackerId;
    private java.util.UUID userId;
    @NotBlank(message = "Stock symbol is required")
    private String stockSymbol;
    private Integer status;
    @JsonProperty("current_partition_index")
    private Integer currentPartitionIndex;

    @JsonProperty("initial_invested_amount")
    @Builder.Default
    @Min(value = 0, message = "Initial invested amount cannot be negative")
    private Integer initialInvestedAmount = 0;

    @JsonProperty("initial_shares_held")
    @Builder.Default
    @Min(value = 0, message = "Initial shares held cannot be negative")
    private Integer initialSharesHeld = 0;

    @JsonProperty("conviction_period_years")
    @NotNull(message = "Conviction period years is required")
    @Min(value = 1, message = "Conviction period must be at least 1 year")
    private Integer convictionPeriodYears;

    @JsonProperty("total_capital_planned")
    @NotNull(message = "Total capital planned is required")
    @Min(value = 1, message = "Total capital planned must be at least 1")
    private Integer totalCapitalPlanned;

    @JsonProperty("partition_days")
    @NotNull(message = "Partition days is required")
    @Min(value = 1, message = "Partition days must be at least 1")
    private Integer partitionDays;

    @JsonProperty("deployment_style")
    @NotNull(message = "Deployment style is required")
    @ValidEnum(enumClass = com.dsip.backend.enums.DeploymentStyle.class, message = "Invalid deployment style")
    private Integer deploymentStyle;

    @JsonProperty("base_conviction_score")
    @NotNull(message = "Base conviction score is required")
    @Min(value = 0, message = "Conviction score cannot be less than 0")
    @Max(value = 100, message = "Conviction score cannot exceed 100")
    private Integer baseConvictionScore;

    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;

    public static DsipTrackerDto fromEntity(com.dsip.backend.entity.DsipTracker tracker) {
        return DsipTrackerDto.builder()
                .trackerId(tracker.getTrackerId())
                .userId(tracker.getUserId())
                .stockSymbol(tracker.getStockSymbol())
                .convictionPeriodYears(tracker.getConvictionPeriodYears())
                .totalCapitalPlanned(tracker.getTotalCapitalPlanned())
                .partitionDays(tracker.getPartitionDays())
                .deploymentStyle(tracker.getDeploymentStyle())
                .baseConvictionScore(tracker.getBaseConvictionScore())
                .initialInvestedAmount(tracker.getInitialInvestedAmount())
                .initialSharesHeld(tracker.getInitialSharesHeld())
                .status(tracker.getStatus())
                .currentPartitionIndex(tracker.getCurrentPartitionIndex())
                .createdAt(tracker.getCreatedAt())
                .updatedAt(tracker.getUpdatedAt())
                .build();
    }
}
