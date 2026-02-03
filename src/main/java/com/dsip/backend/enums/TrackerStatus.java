package com.dsip.backend.enums;

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

    public static TrackerStatus fromValue(int value) {
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TrackerStatus value: " + value));
    }
}
