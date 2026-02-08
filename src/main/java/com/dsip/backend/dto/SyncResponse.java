package com.dsip.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("dsip_shares")
    private Double dsipShares;

    @JsonProperty("dsip_capital_deployed")
    private Double dsipCapitalDeployed;

    @JsonProperty("total_shares")
    private Double totalShares;

    @JsonProperty("total_invested_amount")
    private Double totalInvestedAmount;

    public static SyncResponse success(String message, Double dsipShares, Double dsipCapitalDeployed,
                                        Double totalShares, Double totalInvestedAmount) {
        return SyncResponse.builder()
                .success(true)
                .message(message)
                .dsipShares(dsipShares)
                .dsipCapitalDeployed(dsipCapitalDeployed)
                .totalShares(totalShares)
                .totalInvestedAmount(totalInvestedAmount)
                .build();
    }

    public static SyncResponse blocked(String message) {
        return SyncResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
