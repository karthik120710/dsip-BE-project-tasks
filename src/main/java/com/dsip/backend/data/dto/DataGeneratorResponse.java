package com.dsip.backend.data.dto;

import com.dsip.backend.simulation.SimulationResult;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Response DTO for data generation.
 */
@Data
@Builder
public class DataGeneratorResponse {

    /**
     * Whether the operation was successful.
     */
    private boolean success;

    /**
     * Error message if not successful.
     */
    private String error;

    /**
     * Symbol that was fetched.
     */
    private String symbol;

    /**
     * Path to the generated CSV file.
     */
    private String filePath;

    /**
     * File name only.
     */
    private String fileName;

    /**
     * Number of records (trading days) in the file.
     */
    private int recordCount;

    /**
     * Date range of the data.
     */
    private DateRange dateRange;

    /**
     * Price summary.
     */
    private PriceSummary priceSummary;

    /**
     * Simulation result if simulation was run.
     */
    private SimulationResult simulationResult;

    @Data
    @Builder
    public static class DateRange {
        private LocalDate start;
        private LocalDate end;
        private int tradingDays;
    }

    @Data
    @Builder
    public static class PriceSummary {
        private double startPrice;
        private double endPrice;
        private double highPrice;
        private double lowPrice;
        private double priceChangePct;
    }

    /**
     * Create error response.
     */
    public static DataGeneratorResponse error(String symbol, String message) {
        return DataGeneratorResponse.builder()
                .success(false)
                .symbol(symbol)
                .error(message)
                .build();
    }
}
