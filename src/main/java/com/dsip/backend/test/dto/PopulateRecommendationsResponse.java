package com.dsip.backend.test.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopulateRecommendationsResponse {

    private boolean success;
    private String error;

    @JsonProperty("tracker_id")
    private Integer trackerId;

    @JsonProperty("stock_symbol")
    private String stockSymbol;

    @JsonProperty("csv_file_path")
    private String csvFilePath;

    @JsonProperty("days_processed")
    private int daysProcessed;

    public static PopulateRecommendationsResponse error(String message) {
        return PopulateRecommendationsResponse.builder()
                .success(false)
                .error(message)
                .build();
    }
}
