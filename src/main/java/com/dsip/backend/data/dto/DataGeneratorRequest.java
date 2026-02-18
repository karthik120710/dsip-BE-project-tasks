package com.dsip.backend.data.dto;

import com.dsip.backend.simulation.SimulationController.SimulationRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for generating historical data CSV.
 */
@Data
public class DataGeneratorRequest {

    /**
     * Stock/ETF/Crypto symbol.
     * Examples:
     * - Indian NSE: RELIANCE.NS, TCS.NS, INFY.NS
     * - Indian BSE: RELIANCE.BO, TCS.BO
     * - US Stocks: AAPL, GOOGL, MSFT
     * - US ETFs: SPY, QQQ, VTI
     * - Crypto: BTC-USD, ETH-USD
     */
    @NotBlank(message = "Symbol is required")
    private String symbol;

    /**
     * Preset period for data.
     * Options: 1Y, 2Y, 3Y, 5Y, 10Y, MAX
     * If provided, startDate and endDate are ignored.
     */
    private String period;

    /**
     * Custom start date (YYYY-MM-DD).
     * Used when period is not provided.
     */
    private LocalDate startDate;

    /**
     * Custom end date (YYYY-MM-DD).
     * Used when period is not provided. Defaults to today.
     */
    private LocalDate endDate;

    /**
     * Whether to run simulation after generating CSV.
     */
    private boolean runSimulation = false;

    /**
     * Simulation configuration (if runSimulation is true).
     */
    private SimulationRequest simulationConfig;

    /**
     * Get effective start date based on period or custom date.
     */
    public LocalDate getEffectiveStartDate() {
        if (period != null && !period.isBlank()) {
            int days = com.dsip.backend.data.DataGeneratorProperties.periodToDays(period);
            return LocalDate.now().minusDays(days);
        }
        return startDate != null ? startDate : LocalDate.now().minusYears(3);
    }

    /**
     * Get effective end date.
     */
    public LocalDate getEffectiveEndDate() {
        return endDate != null ? endDate : LocalDate.now();
    }
}
