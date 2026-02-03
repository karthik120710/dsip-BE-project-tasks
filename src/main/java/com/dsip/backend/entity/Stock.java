package com.dsip.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    private Long id;

    private String stockSymbol;

    private String stockName;

    private Exchange listedExchange;

    private Double lastDateMarketClosingPrice;

    /**
     * Timestamp (UTC) when the closing price was last updated.
     */
    private Instant lastUpdatedDate;
}
