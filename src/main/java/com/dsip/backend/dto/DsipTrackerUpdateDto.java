package com.dsip.backend.dto;

import com.dsip.backend.constants.DsipConstants;
import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.TrackerStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DsipTrackerUpdateDto {

    @JsonProperty("deployment_style")
    private DeploymentStyle deploymentStyle;

    @JsonProperty("base_conviction_score")
    @Min(value = 0, message = "Conviction score cannot be less than 0")
    @Max(value = 100, message = "Conviction score cannot exceed 100")
    private Integer baseConvictionScore;

    @JsonProperty("status")
    private TrackerStatus status;

    @JsonProperty("total_capital_planned")
    @Min(value = 1, message = "Total capital planned must be at least 1")
    private Double totalCapitalPlanned;

    @JsonProperty("conviction_period_years")
    @Min(value = 1, message = "Conviction period years must be at least 1")
    private Double convictionPeriodYears;

    // Internal field (stored in DB as days)
    @JsonIgnore
    private Integer partitionDays;

    // API field (exposed as months)
    @JsonProperty("partition_months")
    @Min(value = 1, message = "Partition months must be at least 1")
    private Integer partitionMonths;

    // Conversion: months to days when setting partitionMonths
    public void setPartitionMonths(Integer months) {
        this.partitionMonths = months;
        this.partitionDays = months != null ? months * DsipConstants.DAYS_PER_MONTH : null;
    }
}
