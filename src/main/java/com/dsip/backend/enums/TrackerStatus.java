package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum TrackerStatus {
    ACTIVE(1),
    PAUSED(2),
    COMPLETED(3);

    private final int value;

    @JsonValue
    public String toValue() {
        return name();
    }

    public static TrackerStatus fromValue(int value) {
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TrackerStatus value: " + value));
    }

    @JsonCreator
    public static TrackerStatus fromString(String key) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TrackerStatus: " + key));
    }
}
