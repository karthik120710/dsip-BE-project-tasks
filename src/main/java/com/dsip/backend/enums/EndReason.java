package com.dsip.backend.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum EndReason {
    SUCCESS(
            "SUCCESS",
            "Target Achieved",
            "This investment cycle has successfully reached its expected growth level.\n\n" +
                    "Capital Deployed: {deployedAmount}\n" +
                    "Net Return: {profitPct}%\n\n" +
                    "We are starting the next cycle to continue compounding."),
    KILL_SWITCH_STAGNATION(
            "KILL_SWITCH_STAGNATION",
            "Cycle Stopped – No Meaningful Progress",
            "The stock has not shown sufficient recovery within the expected timeframe.\n\n" +
                    "Capital Deployed: {deployedAmount}\n" +
                    "Net Return: {profitPct}%\n\n" +
                    "The cycle has been closed to preserve capital efficiency."),
    KILL_SWITCH_POOR_GROWTH(
            "KILL_SWITCH_POOR_GROWTH",
            "Cycle Stopped – Risk Protection Triggered",
            "The stock experienced a significant drawdown beyond acceptable limits.\n\n" +
                    "Capital Deployed: {deployedAmount}\n" +
                    "Net Return: {profitPct}%\n\n" +
                    "The system has stopped this cycle to protect remaining capital."),
    KILL_SWITCH_ZOMBIE(
            "KILL_SWITCH_ZOMBIE",
            "Cycle Closed – Capital Fully Utilized",
            "Most of the allocated capital was deployed, but the expected growth was not achieved.\n\n" +
                    "Capital Deployed: {deployedAmount}\n" +
                    "Net Return: {profitPct}%\n\n" +
                    "A new cycle will begin with a fresh allocation."),
    NEUTRAL_PARTITION(
            "NEUTRAL_PARTITION",
            "Cycle Closed – Moderate Outcome",
            "This cycle generated positive returns but did not fully reach the target.\n\n" +
                    "Capital Deployed: {deployedAmount}\n" +
                    "Net Return: {profitPct}%\n\n" +
                    "The strategy continues with disciplined capital rotation.");

    private final String code;
    private final String title;
    private final String messageTemplate;

    public String buildMessage(double deployedAmount, double profitPct) {
        return messageTemplate
                .replace("{deployedAmount}", String.format("%.0f", deployedAmount))
                .replace("{profitPct}", String.format("%.1f", profitPct));
    }

    @JsonValue
    public String toValue() {
        return code;
    }

    @JsonCreator
    public static EndReason fromString(String key) {
        return Arrays.stream(values())
                .filter(reason -> reason.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown EndReason: " + key));
    }

    /**
     * Converts this EndReason to the corresponding PartitionStatus for database
     * persistence.
     * 
     * @return the PartitionStatus that should be saved when a partition ends with
     *         this reason
     */
    public PartitionStatus toPartitionStatus() {
        return switch (this) {
            case SUCCESS -> PartitionStatus.COMPLETED;
            case KILL_SWITCH_STAGNATION -> PartitionStatus.KILL_SWITCH_STAGNATION;
            case KILL_SWITCH_POOR_GROWTH -> PartitionStatus.KILL_SWITCH_POOR_GROWTH;
            case KILL_SWITCH_ZOMBIE -> PartitionStatus.KILL_SWITCH_ZOMBIE;
            case NEUTRAL_PARTITION -> PartitionStatus.NEUTRAL;
        };
    }

    public static EndReason fromPartitionStatus(PartitionStatus status) {
        if (status == PartitionStatus.COMPLETED) {
            return SUCCESS;
        }
        if (status == PartitionStatus.KILL_SWITCH_STAGNATION) {
            return KILL_SWITCH_STAGNATION;
        }
        if (status == PartitionStatus.KILL_SWITCH_POOR_GROWTH) {
            return KILL_SWITCH_POOR_GROWTH;
        }
        if (status == PartitionStatus.KILL_SWITCH_ZOMBIE) {
            return KILL_SWITCH_ZOMBIE;
        }
        if (status == PartitionStatus.NEUTRAL) {
            return NEUTRAL_PARTITION;
        }
        return null; // For ACTIVE or other statuses
    }
}
