package com.dsip.backend.simulation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads OHLC price data from CSV files.
 *
 * Supports flexible CSV formats with automatic column detection.
 */
@Component
@Slf4j
public class CsvPriceDataLoader {

    // Common date formats to try
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    /**
     * Load OHLC data from a CSV file path.
     *
     * @param filePath path to the CSV file
     * @return list of OHLC data sorted by date ascending
     */
    public List<OhlcData> loadFromFile(String filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Path.of(filePath))) {
            return parseCSV(reader);
        }
    }

    /**
     * Load OHLC data from an input stream.
     *
     * @param inputStream CSV data stream
     * @return list of OHLC data sorted by date ascending
     */
    public List<OhlcData> loadFromStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return parseCSV(reader);
        }
    }

    /**
     * Load OHLC data from CSV content string.
     *
     * @param csvContent CSV data as string
     * @return list of OHLC data sorted by date ascending
     */
    public List<OhlcData> loadFromString(String csvContent) {
        String[] lines = csvContent.split("\n");
        return parseLines(List.of(lines));
    }

    private List<OhlcData> parseCSV(BufferedReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        return parseLines(lines);
    }

    private List<OhlcData> parseLines(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        // Parse header to find column indices
        String headerLine = lines.get(0).trim();
        ColumnMapping mapping = detectColumns(headerLine);

        List<OhlcData> result = new ArrayList<>();
        DateTimeFormatter detectedFormat = null;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            try {
                String[] values = parseLine(line);

                // Parse date
                String dateStr = values[mapping.dateIndex].trim();
                LocalDate date = null;

                if (detectedFormat != null) {
                    date = LocalDate.parse(dateStr, detectedFormat);
                } else {
                    // Try to detect date format
                    for (DateTimeFormatter fmt : DATE_FORMATS) {
                        try {
                            date = LocalDate.parse(dateStr, fmt);
                            detectedFormat = fmt;
                            break;
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot parse date: " + dateStr);
                    }
                }

                // Parse OHLC values
                double open = parseDouble(values[mapping.openIndex]);
                double high = parseDouble(values[mapping.highIndex]);
                double low = parseDouble(values[mapping.lowIndex]);
                double close = parseDouble(values[mapping.closeIndex]);

                Long volume = null;
                if (mapping.volumeIndex >= 0 && mapping.volumeIndex < values.length) {
                    String volStr = values[mapping.volumeIndex].trim();
                    if (!volStr.isEmpty() && !volStr.equals("-")) {
                        volume = parseLong(volStr);
                    }
                }

                result.add(OhlcData.builder()
                        .date(date)
                        .open(open)
                        .high(high)
                        .low(low)
                        .close(close)
                        .volume(volume)
                        .build());

            } catch (Exception e) {
                log.warn("Skipping line {}: {} - Error: {}", i + 1, line, e.getMessage());
            }
        }

        // Sort by date ascending
        result.sort((a, b) -> a.getDate().compareTo(b.getDate()));

        log.info("Loaded {} OHLC records from {} to {}",
                result.size(),
                result.isEmpty() ? "N/A" : result.get(0).getDate(),
                result.isEmpty() ? "N/A" : result.get(result.size() - 1).getDate());

        return result;
    }

    private String[] parseLine(String line) {
        // Handle both comma and semicolon delimiters
        if (line.contains(";")) {
            return line.split(";");
        }

        // Handle quoted values with commas inside
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());

        return values.toArray(new String[0]);
    }

    private ColumnMapping detectColumns(String headerLine) {
        String[] headers = parseLine(headerLine.toLowerCase());

        int dateIdx = -1, openIdx = -1, highIdx = -1, lowIdx = -1, closeIdx = -1, volIdx = -1;

        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().replaceAll("[\"']", "");

            if (dateIdx < 0 && matchesAny(h, "date", "time", "timestamp", "datetime", "trading_date")) {
                dateIdx = i;
            } else if (openIdx < 0 && matchesAny(h, "open", "o", "open_price", "opening")) {
                openIdx = i;
            } else if (highIdx < 0 && matchesAny(h, "high", "h", "high_price", "highest")) {
                highIdx = i;
            } else if (lowIdx < 0 && matchesAny(h, "low", "l", "low_price", "lowest")) {
                lowIdx = i;
            } else if (closeIdx < 0 && matchesAny(h, "close", "c", "close_price", "closing", "adj close", "adj_close")) {
                closeIdx = i;
            } else if (volIdx < 0 && matchesAny(h, "volume", "vol", "v", "qty", "quantity")) {
                volIdx = i;
            }
        }

        // Validate required columns
        if (dateIdx < 0) throw new IllegalArgumentException("Missing 'date' column in CSV header");
        if (openIdx < 0) throw new IllegalArgumentException("Missing 'open' column in CSV header");
        if (highIdx < 0) throw new IllegalArgumentException("Missing 'high' column in CSV header");
        if (lowIdx < 0) throw new IllegalArgumentException("Missing 'low' column in CSV header");
        if (closeIdx < 0) throw new IllegalArgumentException("Missing 'close' column in CSV header");

        log.debug("Detected columns - date:{}, open:{}, high:{}, low:{}, close:{}, volume:{}",
                dateIdx, openIdx, highIdx, lowIdx, closeIdx, volIdx);

        return new ColumnMapping(dateIdx, openIdx, highIdx, lowIdx, closeIdx, volIdx);
    }

    private boolean matchesAny(String value, String... options) {
        for (String opt : options) {
            if (value.equals(opt) || value.contains(opt)) {
                return true;
            }
        }
        return false;
    }

    private double parseDouble(String value) {
        String cleaned = value.trim()
                .replaceAll("[\"',]", "")
                .replaceAll("\\s+", "");
        return Double.parseDouble(cleaned);
    }

    private long parseLong(String value) {
        String cleaned = value.trim()
                .replaceAll("[\"',]", "")
                .replaceAll("\\s+", "");
        // Handle values like "1.5M" or "1500000"
        if (cleaned.endsWith("M") || cleaned.endsWith("m")) {
            return (long) (Double.parseDouble(cleaned.substring(0, cleaned.length() - 1)) * 1_000_000);
        }
        if (cleaned.endsWith("K") || cleaned.endsWith("k")) {
            return (long) (Double.parseDouble(cleaned.substring(0, cleaned.length() - 1)) * 1_000);
        }
        return (long) Double.parseDouble(cleaned);
    }

    private record ColumnMapping(int dateIndex, int openIndex, int highIndex, int lowIndex, int closeIndex, int volumeIndex) {}
}
