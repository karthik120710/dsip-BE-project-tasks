package com.dsip.backend.test.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for generating test price data CSV.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePriceDataRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Start date is required")
    @JsonProperty("start_date")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonProperty("end_date")
    private LocalDate endDate;

    /**
     * Default conviction score for all days in the CSV.
     * Can be modified in the CSV before running the workflow.
     */
    @JsonProperty("default_conviction_score")
    @Builder.Default
    private Integer defaultConvictionScore = 9;
}
