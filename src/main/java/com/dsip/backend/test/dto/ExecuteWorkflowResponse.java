package com.dsip.backend.test.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the execute workflow endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteWorkflowResponse {

    private boolean success;
    private String error;

    // Tracker info
    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("stock_symbol")
    private String stockSymbol;

    // Summary
    private Summary summary;

    // Execution log CSV
    @JsonProperty("execution_log")
    private ExecutionLog executionLog;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        @JsonProperty("days_processed")
        private int daysProcessed;

        @JsonProperty("partitions_created")
        private int partitionsCreated;

        @JsonProperty("total_capital_invested")
        private double totalCapitalInvested;

        @JsonProperty("total_shares_acquired")
        private double totalSharesAcquired;

        @JsonProperty("final_portfolio_value")
        private double finalPortfolioValue;

        @JsonProperty("overall_return_pct")
        private double overallReturnPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionLog {
        @JsonProperty("file_path")
        private String filePath;

        @JsonProperty("file_name")
        private String fileName;
    }

    public static ExecuteWorkflowResponse error(String error) {
        return ExecuteWorkflowResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
