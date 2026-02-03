package com.dsip.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PartitionStatus {
    ACTIVE(1),
    COMPLETED(2);

    private final int value;

    public static PartitionStatus fromValue(int value) {
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown PartitionStatus value: " + value));
    }
}
