package com.dsip.backend.service;

import com.dsip.backend.config.AppProperties;
import com.dsip.backend.dto.CompanyDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Service for fetching US stock data from Finnhub API.
 * Handles daily candle data and company profile information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinnhubService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetches the closing price for a US stock on a specific date.
     * Uses Finnhub's stock candles endpoint (daily resolution).
     *
     * @param symbol stock symbol (e.g., "AAPL")
     * @param date the date to fetch closing price for
     * @return closing price, or null if not available
     */
    public Double fetchClosingPrice(String symbol, LocalDate date) {
        try {
            log.info("Fetching closing price from Finnhub API for symbol: {}, date: {}", symbol, date);

            // Convert LocalDate to UNIX timestamps (start and end of day)
            long fromTimestamp = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            long toTimestamp = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();

            String apiKey = appProperties.getFinnhub().getApiKey();
            String url = String.format(
                    "https://finnhub.io/api/v1/stock/candle?symbol=%s&resolution=D&from=%d&to=%d&token=%s",
                    symbol, fromTimestamp, toTimestamp, apiKey
            );

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            // Check if data is available
            if (root.has("s") && "ok".equals(root.get("s").asText())) {
                JsonNode closePrices = root.get("c");
                if (closePrices != null && closePrices.isArray() && closePrices.size() > 0) {
                    double closePrice = closePrices.get(0).asDouble();
                    log.info("Successfully fetched closing price: {} for {}", closePrice, symbol);
                    return closePrice;
                }
            }

            log.warn("No closing price data available for {} on {}", symbol, date);
            return null;

        } catch (Exception e) {
            log.error("Error fetching data from Finnhub API for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch data from Finnhub: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches company profile information from Finnhub.
     *
     * @param symbol stock symbol
     * @return CompanyDetails object with company information
     */
    public CompanyDetails fetchCompanyDetails(String symbol) {
        try {
            log.info("Fetching company details from Finnhub for symbol: {}", symbol);

            String apiKey = appProperties.getFinnhub().getApiKey();
            String url = String.format(
                    "https://finnhub.io/api/v1/stock/profile2?symbol=%s&token=%s",
                    symbol, apiKey
            );

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            return CompanyDetails.builder()
                    .name(root.has("name") ? root.get("name").asText() : symbol)
                    .symbol(symbol)
                    .exchange(root.has("exchange") ? root.get("exchange").asText() : "US")
                    .industry(root.has("finnhubIndustry") ? root.get("finnhubIndustry").asText() : "N/A")
                    .country(root.has("country") ? root.get("country").asText() : "US")
                    .build();

        } catch (Exception e) {
            log.error("Error fetching company details from Finnhub for symbol: {}", symbol, e);
            // Return basic details on error
            return CompanyDetails.builder()
                    .name(symbol)
                    .symbol(symbol)
                    .exchange("US")
                    .industry("N/A")
                    .country("US")
                    .build();
        }
    }
}
