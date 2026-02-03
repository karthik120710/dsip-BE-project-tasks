package com.dsip.backend.service;

import com.dsip.backend.config.AppProperties;
import com.dsip.backend.dto.CompanyDetails;
import com.dsip.backend.exception.StockPriceFetchException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


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
     * Fetches the current/previous close price for a US stock.
     * Uses Finnhub's quote endpoint (available on free tier).
     *
     * @param symbol stock symbol (e.g., "AAPL")
     * @return closing price, or null if not available
     */
    public Double fetchClosingPrice(String symbol) {
        try {
            log.info("Fetching quote from Finnhub API for symbol: {}", symbol);

            String apiKey = appProperties.getFinnhub().getApiKey();
            String url = String.format(
                    "https://finnhub.io/api/v1/quote?symbol=%s&token=%s",
                    symbol, apiKey
            );

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            // "pc" = previous close price, "c" = current price
            if (root.has("pc") && root.get("pc").asDouble() > 0) {
                double closePrice = root.get("pc").asDouble();
                log.info("Successfully fetched previous close price: {} for {}", closePrice, symbol);
                return closePrice;
            }

            log.warn("No quote data available for {}", symbol);
            return null;

        } catch (Exception e) {
            log.error("Error fetching data from Finnhub API for symbol: {}", symbol, e);
            throw new StockPriceFetchException(symbol, "Finnhub API error: " + e.getMessage(), e);
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
