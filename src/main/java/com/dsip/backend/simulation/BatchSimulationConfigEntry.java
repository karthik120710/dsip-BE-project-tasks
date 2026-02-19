package com.dsip.backend.simulation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Parsed configuration entry from one row of the batch simulation CSV.
 */
@Data
@Builder
public class BatchSimulationConfigEntry {
    private String tickerSymbol;
    private LocalDate startDate;
    private LocalDate endDate;
    private String deploymentStyle;
    private double totalCapital;
    private double convictionPeriodYears;
    private int expectedPartitionDays;
    private int convictionScore;
    @Builder.Default
    private double initialInvestedAmount = 0.0;
    @Builder.Default
    private double initialSharesHeld = 0.0;
}
