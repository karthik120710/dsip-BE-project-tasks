package com.dsip.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Entity representing a stock's daily closing price data.
 * This table acts as a cache - once a stock symbol is stored,
 * external API calls are avoided for subsequent requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    /**
     * Unique identifier for the stock record.
     */
    private Long id;

    /**
     * Stock symbol (e.g., "AAPL" for US, "RELIANCE" for NSE).
     * MUST be unique across the table.
     */
    private String stockSymbol;

    /**
     * Company name (e.g., "Apple Inc.", "Reliance Industries").
     */
    private String stockName;

    /**
     * Exchange where the stock is listed (US, NSE, or BSE).
     */
    private Exchange listedExchange;

    /**
     * Last recorded market closing price for the stock.
     */
    private Double lastDateMarketClosingPrice;

    /**
     * Date when the closing price was last updated.
     */
    private LocalDate lastUpdatedDate;
}
