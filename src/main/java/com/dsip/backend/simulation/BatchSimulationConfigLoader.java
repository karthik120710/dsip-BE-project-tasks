package com.dsip.backend.simulation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads and parses batch simulation configuration from a CSV file.
 *
 * Expected CSV format (comma-separated):
 * TICKER SYMBOL,START DATE,END DATE,LOAD FACTOR MODE,ANNUAL_BUDGET_INR,CONVICTION_PERIOD,PARTION_length,CONVICTION_SCORE,INITIAL_INVESTED_AMOUNT,INITIAL_SHARES_HELD
 * BFLY,MAY 7 2024,FEB 13 2025,AGGRESSIVE,100000,0.5,2,80,0,0
 *
 * Notes:
 * - CONVICTION_PERIOD is in years (e.g., 0.5, 1, 3)
 * - PARTION_length is in months (converted to trading days at ~21 days/month)
 * - CONVICTION_SCORE, INITIAL_INVESTED_AMOUNT, INITIAL_SHARES_HELD are optional (defaults: 80, 0, 0)
 * - Stock type is resolved from the DB, not from the CSV
 */
@Service
@Slf4j
public class BatchSimulationConfigLoader {

    private static final int TRADING_DAYS_PER_MONTH = 21;

    /**
     * Date formatter that handles full month names (case-insensitive).
     */
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d yyyy")
            .toFormatter(Locale.ENGLISH);

    /**
     * Load and parse the config CSV from the given file path.
     *
     * @param csvPath path to the CSV config file
     * @return list of parsed config entries
     * @throws IOException if the file cannot be read
     */
    public List<BatchSimulationConfigEntry> load(String csvPath) throws IOException {
        Path path = Path.of(csvPath);
        if (!Files.exists(path)) {
            throw new IOException("Config CSV file not found: " + csvPath);
        }

        List<BatchSimulationConfigEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String headerLine = reader.readLine(); // skip header
            if (headerLine == null) {
                throw new IOException("Config CSV file is empty: " + csvPath);
            }
            log.info("Batch config CSV header: {}", headerLine);

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    entries.add(parseLine(line, lineNum));
                } catch (Exception e) {
                    log.error("Failed to parse line {}: '{}' - {}", lineNum, line, e.getMessage());
                    throw new IOException("Error parsing line " + lineNum + ": " + e.getMessage(), e);
                }
            }
        }

        log.info("Loaded {} stock configurations from {}", entries.size(), csvPath);
        return entries;
    }

    private BatchSimulationConfigEntry parseLine(String line, int lineNum) {
        String[] parts = line.split(",");
        if (parts.length < 7) {
            throw new IllegalArgumentException(
                    "Expected at least 7 columns but found " + parts.length + " at line " + lineNum);
        }

        // Required columns (indices 0-6)
        String ticker = parts[0].trim().toUpperCase();
        LocalDate startDate = parseDate(parts[1].trim());
        LocalDate endDate = parseDate(parts[2].trim());
        String deploymentStyle = parts[3].trim().toUpperCase();
        double totalCapital = Double.parseDouble(parts[4].trim());
        double convictionPeriodYears = Double.parseDouble(parts[5].trim());
        int partitionMonths = Integer.parseInt(parts[6].trim());
        int expectedPartitionDays = partitionMonths * TRADING_DAYS_PER_MONTH;

        // Optional columns with defaults
        int convictionScore = optionalInt(parts, 7, 80);
        double initialInvestedAmount = optionalDouble(parts, 8, 0.0);
        double initialSharesHeld = optionalDouble(parts, 9, 0.0);

        return BatchSimulationConfigEntry.builder()
                .tickerSymbol(ticker)
                .startDate(startDate)
                .endDate(endDate)
                .deploymentStyle(deploymentStyle)
                .totalCapital(totalCapital)
                .convictionPeriodYears(convictionPeriodYears)
                .expectedPartitionDays(expectedPartitionDays)
                .convictionScore(convictionScore)
                .initialInvestedAmount(initialInvestedAmount)
                .initialSharesHeld(initialSharesHeld)
                .build();
    }

    private static int optionalInt(String[] parts, int index, int defaultValue) {
        if (parts.length > index && !parts[index].trim().isEmpty()) {
            return Integer.parseInt(parts[index].trim());
        }
        return defaultValue;
    }

    private static double optionalDouble(String[] parts, int index, double defaultValue) {
        if (parts.length > index && !parts[index].trim().isEmpty()) {
            return Double.parseDouble(parts[index].trim());
        }
        return defaultValue;
    }

    /**
     * Parse date strings like "MAY 7 2024", "MARCH 10 2025", "FEB 13 2025".
     * Handles both abbreviated and full month names.
     */
    static LocalDate parseDate(String dateStr) {
        String normalized = expandMonthAbbreviation(dateStr.trim().toUpperCase());
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Cannot parse date: '" + dateStr + "' (normalized: '" + normalized + "'). "
                    + "Expected format like 'MAY 7 2024' or 'MARCH 10 2025'", e);
        }
    }

    /**
     * Expand common month abbreviations to full names for parsing.
     */
    private static String expandMonthAbbreviation(String dateStr) {
        if (dateStr.startsWith("JAN ")) return "JANUARY" + dateStr.substring(3);
        if (dateStr.startsWith("FEB ")) return "FEBRUARY" + dateStr.substring(3);
        if (dateStr.startsWith("MAR ")) return "MARCH" + dateStr.substring(3);
        if (dateStr.startsWith("APR ")) return "APRIL" + dateStr.substring(3);
        if (dateStr.startsWith("JUN ")) return "JUNE" + dateStr.substring(3);
        if (dateStr.startsWith("JUL ")) return "JULY" + dateStr.substring(3);
        if (dateStr.startsWith("AUG ")) return "AUGUST" + dateStr.substring(3);
        if (dateStr.startsWith("SEP ")) return "SEPTEMBER" + dateStr.substring(3);
        if (dateStr.startsWith("OCT ")) return "OCTOBER" + dateStr.substring(3);
        if (dateStr.startsWith("NOV ")) return "NOVEMBER" + dateStr.substring(3);
        if (dateStr.startsWith("DEC ")) return "DECEMBER" + dateStr.substring(3);
        return dateStr;
    }
}
