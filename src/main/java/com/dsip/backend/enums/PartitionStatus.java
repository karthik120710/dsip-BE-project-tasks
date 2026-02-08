package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PartitionStatus {
    ACTIVE(1),
    COMPLETED(2),
    KILL_SWITCH(3),
    NEUTRAL(4);

    private final int value;

    @JsonValue
    public String toValue() {
        return name();
    }

    public static PartitionStatus fromValue(int value) {
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown PartitionStatus value: " + value));
    }

    @JsonCreator
    public static PartitionStatus fromString(String key) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown PartitionStatus: " + key));
    }
}
