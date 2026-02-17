package com.dsip.backend.data;

import com.dsip.backend.simulation.OhlcData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource.YahooFinanceInterval;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching historical OHLCV data from Yahoo Finance using ta4j library.
 *
 * Uses ta4j's YahooFinanceHttpBarSeriesDataSource which handles authentication internally.
 *
 * Symbol formats:
 * - Indian NSE: RELIANCE.NS, TCS.NS, INFY.NS
 * - Indian BSE: RELIANCE.BO, TCS.BO
 * - US Stocks: AAPL, GOOGL, MSFT
 * - US ETFs: SPY, QQQ
 * - Crypto: BTC-USD, ETH-USD
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalDataService {

    private final DataGeneratorProperties properties;

    // ta4j Yahoo Finance data source with caching enabled
    private final YahooFinanceHttpBarSeriesDataSource yahooDataSource =
            new YahooFinanceHttpBarSeriesDataSource(true);

    /**
     * Fetch historical OHLCV data from Yahoo Finance.
     *
     * @param symbol    Stock/ETF/Crypto symbol
     * @param startDate Start date for data
     * @param endDate   End date for data
     * @return List of OHLC data sorted by date ascending
     * @throws Exception if fetch fails
     */
    public List<OhlcData> fetchHistoricalData(String symbol, LocalDate startDate, LocalDate endDate)
            throws Exception {

        log.info("Fetching historical data for {} from {} to {}", symbol, startDate, endDate);

        // Convert LocalDate to Instant for ta4j
        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Fetch data using ta4j's Yahoo Finance loader
        BarSeries barSeries = yahooDataSource.loadSeriesInstance(
                symbol,
                YahooFinanceInterval.DAY_1,
                start,
                end
        );

        if (barSeries == null || barSeries.isEmpty()) {
            log.warn("No data returned for symbol {} from {} to {}", symbol, startDate, endDate);
            return new ArrayList<>();
        }

        // Convert BarSeries to OhlcData list
        List<OhlcData> result = new ArrayList<>();
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Bar bar = barSeries.getBar(i);

            // Convert Instant to LocalDate
            LocalDate date = bar.getEndTime().atZone(ZoneId.of("America/New_York")).toLocalDate();

            result.add(OhlcData.builder()
                    .date(date)
                    .open(bar.getOpenPrice().doubleValue())
                    .high(bar.getHighPrice().doubleValue())
                    .low(bar.getLowPrice().doubleValue())
                    .close(bar.getClosePrice().doubleValue())
                    .volume(bar.getVolume().longValue())
                    .build());
        }

        // Sort by date ascending
        result.sort((a, b) -> a.getDate().compareTo(b.getDate()));

        log.info("Fetched {} records for {} from {} to {}",
                result.size(), symbol,
                result.isEmpty() ? "N/A" : result.get(0).getDate(),
                result.isEmpty() ? "N/A" : result.get(result.size() - 1).getDate());

        return result;
    }

    /**
     * Validate if a symbol exists on Yahoo Finance.
     *
     * @param symbol Symbol to validate
     * @return true if symbol exists
     */
    public boolean validateSymbol(String symbol) {
        try {
            // Try to fetch last 10 days of data
            List<OhlcData> data = fetchHistoricalData(symbol,
                    LocalDate.now().minusDays(10),
                    LocalDate.now());
            return !data.isEmpty();
        } catch (Exception e) {
            log.warn("Symbol validation failed for {}: {}", symbol, e.getMessage());
            return false;
        }
    }
}
