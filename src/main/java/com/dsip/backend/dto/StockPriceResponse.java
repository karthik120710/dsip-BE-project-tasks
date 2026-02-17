package com.dsip.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for stock closing price requests.
 * Includes price data, source (DB or API), and company details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceResponse {

    /**
     * Stock symbol (e.g., "AAPL", "RELIANCE").
     */
    private String symbol;

    /**
     * Exchange (US, NSE, or BSE).
     */
    private String exchange;

    /**
     * Date of the closing price (YYYY-MM-DD format).
     */
    private String date;

    /**
     * Closing price on the given date.
     */
    private Double closePrice;

    /**
     * Data source: "DB" if from cache, "API" if fetched from external API.
     */
    private String source;

    /**
     * Company details (name, industry, etc.).
     */
    private CompanyDetails company;
}
