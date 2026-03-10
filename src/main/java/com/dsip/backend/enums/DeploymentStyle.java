package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum DeploymentStyle {
    GRADUAL(1, "GRADUAL"),
    MODERATE(2, "MODERATE"),
    AGGRESSIVE(3, "AGGRESSIVE"),
    BALANCED(4, "BALANCED");

    private final int value;
    @JsonValue
    private final String key;

    public static DeploymentStyle fromValue(int value) {
        return Arrays.stream(values())
                .filter(style -> style.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown DeploymentStyle value: " + value));
    }

    @JsonCreator
    public static DeploymentStyle fromKey(String key) {
        return Arrays.stream(values())
                .filter(style -> style.key.equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown DeploymentStyle key: " + key));
    }
}
