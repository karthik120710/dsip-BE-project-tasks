package com.dsip.backend.dto;

import com.dsip.backend.constants.DsipConstants;

import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.TrackerStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonProperty("stock_id")
    private Integer stockId;

    @NotBlank(message = "Stock symbol is required")
    @JsonProperty("stock_symbol")
    private String stockSymbol;

    private TrackerStatus status;

    @JsonProperty("active_partition_index")
    private Integer activePartitionIndex;

    @JsonProperty("total_capital_invested_so_far")
    private Double totalCapitalInvestedSoFar;

    @JsonProperty("shares_held_so_far")
    private Double sharesHeldSoFar;

    @JsonProperty("is_fractional_shares_allowed")
    @Builder.Default
    private Boolean isFractionalSharesAllowed = true;

    @JsonProperty("initial_invested_amount")
    @Builder.Default
    @Min(value = 0, message = "Initial invested amount cannot be negative")
    private Double initialInvestedAmount = 0.0;

    @JsonProperty("initial_shares_held")
    @Builder.Default
    @Min(value = 0, message = "Initial shares held cannot be negative")
    private Double initialSharesHeld = 0.0;

    // Internal field (stored in DB as years)
    @JsonIgnore
    private Double convictionPeriodYears;

    // API field (exposed as months)
    @JsonProperty("conviction_period_months")
    @NotNull(message = "Conviction period months is required")
    @Min(value = 1, message = "Conviction period months must be at least 1")
    private Integer convictionPeriodMonths;

    // Conversion: months → years when setting
    public void setConvictionPeriodMonths(Integer months) {
        this.convictionPeriodMonths = months;
        this.convictionPeriodYears = months != null ? months / 12.0 : null;
    }

    // Conversion: years → months when getting (for response)
    public Integer getConvictionPeriodMonths() {
        if (this.convictionPeriodMonths == null && this.convictionPeriodYears != null) {
            this.convictionPeriodMonths = (int) Math.round(this.convictionPeriodYears * 12);
        }
        return this.convictionPeriodMonths;
    }

    @JsonProperty("total_capital_planned")
    @NotNull(message = "Total capital planned is required")
    @Min(value = 1, message = "Total capital planned must be at least 1")
    private Double totalCapitalPlanned;

    // Internal field (stored in DB as days)
    @JsonIgnore
    private Integer partitionDays;

    // API field (exposed as months)
    @JsonProperty("partition_months")
    @NotNull(message = "Partition months is required")
    @Min(value = 1, message = "Partition months must be at least 1")
    private Integer partitionMonths;

    // Conversion: months to days when setting partitionMonths
    public void setPartitionMonths(Integer months) {
        this.partitionMonths = months;
        this.partitionDays = months != null ? months * DsipConstants.TRADING_DAYS_PER_MONTH : null;
    }

    // Conversion: days to months when getting partitionMonths
    public Integer getPartitionMonths() {
        if (this.partitionMonths == null && this.partitionDays != null) {
            this.partitionMonths = this.partitionDays / DsipConstants.TRADING_DAYS_PER_MONTH;
        }
        return this.partitionMonths;
    }

    @JsonProperty("deployment_style")
    @NotNull(message = "Deployment style is required")
    private DeploymentStyle deploymentStyle;

    @JsonProperty("base_conviction_score")
    @NotNull(message = "Base conviction score is required")
    @Min(value = 0, message = "Conviction score cannot be less than 0")
    @Max(value = 100, message = "Conviction score cannot exceed 100")
    private Integer baseConvictionScore;

    private java.time.Instant createdAt;

    public static DsipTrackerDto fromEntity(com.dsip.backend.entity.DsipTracker tracker, String stockSymbol) {
        DsipTrackerDto dto = DsipTrackerDto.builder()
                .trackerId(tracker.getTrackerId())
                .userId(tracker.getUserId())
                .stockId(tracker.getStockId())
                .stockSymbol(stockSymbol)
                .totalCapitalPlanned(tracker.getTotalCapitalPlanned())
                .partitionDays(tracker.getPartitionDays())
                .partitionMonths(tracker.getPartitionDays() / DsipConstants.TRADING_DAYS_PER_MONTH)
                .deploymentStyle(DeploymentStyle.fromValue(tracker.getDeploymentStyle()))
                .baseConvictionScore(tracker.getBaseConvictionScore())
                .initialInvestedAmount(tracker.getInitialInvestedAmount())
                .initialSharesHeld(tracker.getInitialSharesHeld())
                .status(TrackerStatus.fromValue(tracker.getStatus()))
                .activePartitionIndex(tracker.getActivePartitionIndex())
                .totalCapitalInvestedSoFar(tracker.getTotalCapitalInvestedSoFar())
                .sharesHeldSoFar(tracker.getSharesHeldSoFar())
                .isFractionalSharesAllowed(tracker.getIsFractionalSharesAllowed())
                .createdAt(tracker.getCreatedAt())
                .build();
        // Populate conviction months from years (triggers setter-based conversion)
        if (tracker.getConvictionPeriodYears() != null) {
            dto.setConvictionPeriodMonths((int) Math.round(tracker.getConvictionPeriodYears() * 12));
        }
        return dto;
    }
}
