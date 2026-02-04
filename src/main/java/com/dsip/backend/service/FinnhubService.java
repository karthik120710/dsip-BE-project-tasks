package com.dsip.backend.service;

import com.dsip.backend.dto.CompanyDetails;
import com.dsip.backend.exception.StockPriceFetchException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;


/**
 * Service for fetching US stock data from Finnhub API.
 * Handles daily candle data and company profile information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinnhubService {

    private final FinnhubKeyRotator keyRotator;
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

            String response = executeWithKeyRotation(symbol,
                    apiKey -> String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", symbol, apiKey));

            JsonNode root = objectMapper.readTree(response);

            // "pc" = previous close price, "c" = current price
            if (root.has("pc") && root.get("pc").asDouble() > 0) {
                double closePrice = root.get("pc").asDouble();
                log.info("Successfully fetched previous close price: {} for {}", closePrice, symbol);
                return closePrice;
            }

            log.warn("No quote data available for {}", symbol);
            return null;

        } catch (StockPriceFetchException e) {
            throw e;
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

            String response = executeWithKeyRotation(symbol,
                    apiKey -> String.format("https://finnhub.io/api/v1/stock/profile2?symbol=%s&token=%s", symbol, apiKey));

            JsonNode root = objectMapper.readTree(response);

            return CompanyDetails.builder()
                    .name(root.has("name") ? root.get("name").asText() : symbol)
                    .symbol(symbol)
                    .exchange(root.has("exchange") ? root.get("exchange").asText() : "US")
                    .industry(root.has("finnhubIndustry") ? root.get("finnhubIndustry").asText() : "N/A")
                    .country(root.has("country") ? root.get("country").asText() : "US")
                    .build();

        } catch (StockPriceFetchException e) {
            log.error("All API keys exhausted fetching company details for symbol: {}", symbol, e);
            // Return basic details on rate-limit exhaustion (preserving current fallback behavior)
            return CompanyDetails.builder()
                    .name(symbol)
                    .symbol(symbol)
                    .exchange("US")
                    .industry("N/A")
                    .country("US")
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

    private String executeWithKeyRotation(String symbol, Function<String, String> urlBuilder) {
        int totalKeys = keyRotator.getKeyCount();

        for (int attempt = 0; attempt < totalKeys; attempt++) {
            String apiKey = keyRotator.getKey();
            if (apiKey == null) {
                break;
            }

            try {
                String url = urlBuilder.apply(apiKey);
                return restTemplate.getForObject(url, String.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    log.warn("Rate limited (429) on Finnhub API key, rotating to next key");
                    keyRotator.markCurrentKeyRateLimited();
                } else {
                    throw e;
                }
            }
        }

        // All keys exhausted — wait for earliest cooldown and retry once
        Instant earliest = keyRotator.getEarliestCooldownExpiry();
        if (earliest != null) {
            long waitMs = Duration.between(Instant.now(), earliest).toMillis();
            if (waitMs > 0 && waitMs <= 60_000) {
                log.info("All Finnhub API keys rate-limited. Waiting {}ms for cooldown...", waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new StockPriceFetchException(symbol,
                            "Interrupted while waiting for API key cooldown");
                }

                String apiKey = keyRotator.getKey();
                if (apiKey != null) {
                    try {
                        String url = urlBuilder.apply(apiKey);
                        return restTemplate.getForObject(url, String.class);
                    } catch (HttpClientErrorException e) {
                        if (e.getStatusCode().value() == 429) {
                            keyRotator.markCurrentKeyRateLimited();
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }

        throw new StockPriceFetchException(symbol,
                "All API keys are rate-limited. Please try again later or contact admin.");
    }
}
