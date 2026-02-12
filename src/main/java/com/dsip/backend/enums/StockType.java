package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Stock classification type that determines target return percentage.
 *
 * Target returns:
 * - PENNY: 24% (high volatility stocks)
 * - MIDCAP: 21% (balanced growth stocks)
 * - LARGECAP: 15% (stable blue-chip stocks)
 * - ETF: 15% (index funds)
 */
@Getter
@RequiredArgsConstructor
public enum StockType {
    PENNY(1, "PENNY", 0.24),
    MIDCAP(2, "MIDCAP", 0.21),
    LARGECAP(3, "LARGECAP", 0.15),
    ETF(4, "ETF", 0.15);

    private final int value;
    @JsonValue
    private final String key;
    private final double targetReturn;

    /**
     * Get the target return as a percentage (e.g., 24.0 for 24%)
     */
    public double getTargetReturnPercentage() {
        return targetReturn * 100;
    }

    public static StockType fromValue(int value) {
        return Arrays.stream(values())
                .filter(type -> type.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown StockType value: " + value));
    }

    @JsonCreator
    public static StockType fromKey(String key) {
        return Arrays.stream(values())
                .filter(type -> type.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown StockType key: " + key));
    }
}
