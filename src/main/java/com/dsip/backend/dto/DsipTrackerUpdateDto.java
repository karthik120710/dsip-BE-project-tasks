package com.dsip.backend.dto;

import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.TrackerStatus;
import com.dsip.backend.validation.ValidEnum;
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
    @ValidEnum(enumClass = DeploymentStyle.class, message = "Invalid deployment style")
    private Integer deploymentStyle;

    @JsonProperty("base_conviction_score")
    @Min(value = 0, message = "Conviction score cannot be less than 0")
    @Max(value = 100, message = "Conviction score cannot exceed 100")
    private Integer baseConvictionScore;

    @JsonProperty("status")
    @ValidEnum(enumClass = TrackerStatus.class, message = "Invalid status")
    private Integer status;

    @JsonProperty("total_capital_planned")
    @Min(value = 1, message = "Total capital planned must be at least 1")
    private Double totalCapitalPlanned;

    @JsonProperty("conviction_period_years")
    @Min(value = 1, message = "Conviction period years must be at least 1")
    private Integer convictionPeriodYears;

    @JsonProperty("partition_days")
    @Min(value = 1, message = "Partition days must be at least 1")
    private Integer partitionDays;
}
