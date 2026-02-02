package com.dsip.backend.service;

import com.dsip.backend.config.AppProperties;
import com.dsip.backend.dto.CompanyDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Service for fetching Indian stock data from Upstox API.
 * Handles daily candle data for NSE and BSE exchanges.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpstoxService {

    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fetches the closing price for an Indian stock on a specific date.
     * Uses Upstox's historical candle API with Bearer token authentication.
     *
     * @param symbol stock symbol (e.g., "RELIANCE")
     * @param exchange NSE or BSE
     * @param date the date to fetch closing price for
     * @return closing price, or null if not available
     */
    public Double fetchClosingPrice(String symbol, String exchange, LocalDate date) {
        try {
            log.info("Fetching closing price from Upstox API for symbol: {}, exchange: {}, date: {}",
                     symbol, exchange, date);

            String accessToken = appProperties.getUpstox().getAccessToken();

            // Format instrument key (e.g., "NSE_EQ|INE002A01018" for NSE or "BSE_EQ|500325" for BSE)
            // Note: This is a simplified version. In production, you'd need to map symbols to instrument keys
            String instrumentKey = String.format("%s_EQ|%s", exchange, symbol);

            // Format date for API (YYYY-MM-DD)
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

            String url = String.format(
                    "https://api.upstox.com/v2/historical-candle/%s/day/%s/%s",
                    instrumentKey, dateStr, dateStr
            );

            // Set Bearer token in headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            // Parse candle data
            // Upstox response format: { "status": "success", "data": { "candles": [[timestamp, open, high, low, close, volume, oi]] } }
            if (root.has("status") && "success".equals(root.get("status").asText())) {
                JsonNode candles = root.path("data").path("candles");
                if (candles.isArray() && candles.size() > 0) {
                    // Each candle: [timestamp, open, high, low, close, volume, oi]
                    JsonNode candle = candles.get(0);
                    double closePrice = candle.get(4).asDouble(); // Index 4 is close price
                    log.info("Successfully fetched closing price: {} for {}", closePrice, symbol);
                    return closePrice;
                }
            }

            log.warn("No closing price data available for {} on {} exchange on {}", symbol, exchange, date);
            return null;

        } catch (Exception e) {
            log.error("Error fetching data from Upstox API for symbol: {}, exchange: {}", symbol, exchange, e);
            throw new RuntimeException("Failed to fetch data from Upstox: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches company details for an Indian stock.
     * Note: Upstox may not provide detailed company info via API.
     * This method returns basic information.
     *
     * @param symbol stock symbol
     * @param exchange NSE or BSE
     * @return CompanyDetails object with basic information
     */
    public CompanyDetails fetchCompanyDetails(String symbol, String exchange) {
        try {
            log.info("Fetching company details for Indian stock: {}, exchange: {}", symbol, exchange);

            // For Indian stocks, we'll return basic details
            // In production, you might integrate with a separate company info API
            return CompanyDetails.builder()
                    .name(symbol)
                    .symbol(symbol)
                    .exchange(exchange)
                    .industry("N/A")
                    .country("India")
                    .build();

        } catch (Exception e) {
            log.error("Error fetching company details for symbol: {}, exchange: {}", symbol, exchange, e);
            return CompanyDetails.builder()
                    .name(symbol)
                    .symbol(symbol)
                    .exchange(exchange)
                    .industry("N/A")
                    .country("India")
                    .build();
        }
    }
}
