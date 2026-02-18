package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequest {

    @NotNull(message = "Tracker ID is required")
    @JsonProperty("tracker_id")
    private Integer trackerId;

    @NotNull(message = "Current total shares is required")
    @Min(value = 0, message = "Current total shares cannot be negative")
    @JsonProperty("current_total_shares")
    private Double currentTotalShares;

    @NotNull(message = "Current total invested amount is required")
    @Min(value = 0, message = "Current total invested amount cannot be negative")
    @JsonProperty("current_total_invested_amount")
    private Double currentTotalInvestedAmount;

    @JsonProperty("reason")
    private String reason;
}
