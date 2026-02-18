package com.dsip.backend.service;

import com.dsip.backend.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class FinnhubKeyRotator {

    private final AppProperties appProperties;
    private List<String> keys;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, Instant> rateLimitedKeys = new ConcurrentHashMap<>();

    private static final long COOLDOWN_SECONDS = 60;

    public FinnhubKeyRotator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        keys = appProperties.getFinnhub().getApiKeys().stream()
                .filter(k -> k != null && !k.isBlank())
                .toList();

        if (keys.isEmpty()) {
            log.warn("No Finnhub API keys configured! Stock price fetching will fail.");
        } else {
            log.info("FinnhubKeyRotator initialized with {} API key(s)", keys.size());
        }
    }

    public String getKey() {
        if (keys.isEmpty()) {
            return null;
        }

        Instant now = Instant.now();
        int startIndex = currentIndex.get();

        for (int i = 0; i < keys.size(); i++) {
            int idx = (startIndex + i) % keys.size();
            Instant expiry = rateLimitedKeys.get(idx);
            if (expiry == null || now.isAfter(expiry)) {
                rateLimitedKeys.remove(idx);
                currentIndex.set(idx);
                return keys.get(idx);
            }
        }

        return null;
    }

    public void markCurrentKeyRateLimited() {
        int idx = currentIndex.get();
        Instant expiry = Instant.now().plusSeconds(COOLDOWN_SECONDS);
        rateLimitedKeys.put(idx, expiry);
        log.warn("Finnhub API key #{} rate-limited, cooldown until {}", idx + 1, expiry);

        int nextIdx = (idx + 1) % keys.size();
        currentIndex.set(nextIdx);
    }

    public Instant getEarliestCooldownExpiry() {
        return rateLimitedKeys.values().stream()
                .min(Instant::compareTo)
                .orElse(null);
    }

    public int getKeyCount() {
        return keys.size();
    }
}
