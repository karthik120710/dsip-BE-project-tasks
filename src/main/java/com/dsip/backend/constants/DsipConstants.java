package com.dsip.backend.constants;

/**
 * Application-wide constants for DSIP calculations and conversions.
 */
public final class DsipConstants {

    private DsipConstants() {
        // Prevent instantiation
    }

    /**
     * Number of days in one month for partition calculations.
     * Used to convert between partition_months (API) and partition_days (DB).
     */
    public static final int DAYS_PER_MONTH = 30;

    /**
     * Number of trading days per year.
     * Used for conviction period and cycle calculations.
     */
    public static final int TRADING_DAYS_PER_YEAR = 252;

}
