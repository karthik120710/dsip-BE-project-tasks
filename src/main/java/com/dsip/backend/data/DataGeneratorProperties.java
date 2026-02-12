package com.dsip.backend.data;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the historical data generator.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "dsip.data")
public class DataGeneratorProperties {

    /**
     * Output directory for generated CSV files.
     * Defaults to ./data/historical
     */
    private String outputDir = "./data/historical";

    /**
     * Default period for data fetching if not specified.
     * Options: 1Y, 2Y, 3Y, 5Y, 10Y
     */
    private String defaultPeriod = "3Y";

    /**
     * Yahoo Finance API settings.
     */
    private YahooFinance yahooFinance = new YahooFinance();

    @Data
    public static class YahooFinance {
        /**
         * Connection timeout in seconds.
         */
        private int timeoutSeconds = 30;

        /**
         * Number of retry attempts on failure.
         */
        private int retryCount = 3;

        /**
         * Delay between retries in milliseconds.
         */
        private int retryDelayMs = 1000;

        /**
         * Base URL for Yahoo Finance API.
         */
        private String baseUrl = "https://query1.finance.yahoo.com/v7/finance/download";
    }

    /**
     * Convert period string to days.
     */
    public static int periodToDays(String period) {
        return switch (period.toUpperCase()) {
            case "1Y" -> 365;
            case "2Y" -> 730;
            case "3Y" -> 1095;
            case "5Y" -> 1825;
            case "10Y" -> 3650;
            case "MAX" -> 36500; // ~100 years
            default -> 1095; // Default 3 years
        };
    }
}
