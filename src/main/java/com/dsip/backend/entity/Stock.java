package com.dsip.backend.entity;

import com.dsip.backend.enums.StockType;
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

    /**
     * Stock classification that determines target return percentage.
     * PENNY=24%, MIDCAP=21%, LARGECAP=15%, ETF=15%
     */
    private StockType stockType;

    /**
     * Previous day's closing price. Used for lock-in percentage calculations.
     * Lock-in % = ((lockInPrice - lastDateMarketClosingPrice) / lastDateMarketClosingPrice) * 100
     */
    private Double lastDateMarketClosingPrice;

    /**
     * Timestamp (UTC) when the closing price was last updated.
     */
    private Instant lastUpdatedDate;
}
