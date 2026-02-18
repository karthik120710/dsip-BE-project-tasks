package com.dsip.backend.test.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Response DTO for generate price data endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePriceDataResponse {

    private boolean success;
    private String symbol;

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("record_count")
    private int recordCount;

    @JsonProperty("date_range")
    private DateRange dateRange;

    @JsonProperty("price_summary")
    private PriceSummary priceSummary;

    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        private LocalDate start;
        private LocalDate end;
        @JsonProperty("trading_days")
        private int tradingDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceSummary {
        @JsonProperty("start_price")
        private double startPrice;
        @JsonProperty("end_price")
        private double endPrice;
        @JsonProperty("high_price")
        private double highPrice;
        @JsonProperty("low_price")
        private double lowPrice;
        @JsonProperty("price_change_pct")
        private double priceChangePct;
    }

    public static GeneratePriceDataResponse error(String symbol, String error) {
        return GeneratePriceDataResponse.builder()
                .success(false)
                .symbol(symbol)
                .error(error)
                .build();
    }
}
