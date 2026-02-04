package com.dsip.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum DeploymentStyle {
    GRADUAL(1, "gradual"),
    MODERATE(2, "moderate"),
    AGGRESSIVE(3, "aggressive");

    private final int value;
    private final String key;

    public static DeploymentStyle fromValue(int value) {
        return Arrays.stream(values())
                .filter(style -> style.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown DeploymentStyle value: " + value));
    }
}
