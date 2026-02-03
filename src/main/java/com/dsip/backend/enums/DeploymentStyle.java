package com.dsip.backend.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum DeploymentStyle {
    GRADUAL(1),
    MODERATE(2),
    AGGRESSIVE(3);

    private final int value;

    public static DeploymentStyle fromValue(int value) {
        return Arrays.stream(values())
                .filter(style -> style.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown DeploymentStyle value: " + value));
    }
}
