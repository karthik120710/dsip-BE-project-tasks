package com.dsip.backend.data;

import com.dsip.backend.simulation.OhlcData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for exporting OHLCV data to CSV files.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvExportService {

    private final DataGeneratorProperties properties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CSV_HEADER = "date,open,high,low,close,volume";

    @PostConstruct
    public void init() {
        // Ensure output directory exists
        try {
            Path outputDir = Paths.get(properties.getOutputDir());
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
                log.info("Created output directory: {}", outputDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create output directory: {}", e.getMessage());
        }
    }

    /**
     * Export OHLCV data to a CSV file.
     *
     * @param symbol   Stock symbol
     * @param data     OHLCV data to export
     * @param startDate Start date of data
     * @param endDate   End date of data
     * @return Path to the created CSV file
     * @throws IOException if file creation fails
     */
    public Path exportToCsv(String symbol, List<OhlcData> data, LocalDate startDate, LocalDate endDate)
            throws IOException {

        // Generate filename: SYMBOL_STARTDATE_ENDDATE.csv
        String safeSymbol = sanitizeSymbol(symbol);
        String fileName = String.format("%s_%s_%s.csv",
                safeSymbol,
                startDate.format(DATE_FORMATTER),
                endDate.format(DATE_FORMATTER));

        Path outputPath = Paths.get(properties.getOutputDir(), fileName);

        // Ensure parent directory exists
        Files.createDirectories(outputPath.getParent());

        log.info("Exporting {} records to {}", data.size(), outputPath.toAbsolutePath());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write header
            writer.write(CSV_HEADER);
            writer.newLine();

            // Write data rows
            for (OhlcData ohlc : data) {
                writer.write(formatCsvLine(ohlc));
                writer.newLine();
            }
        }

        log.info("Successfully exported {} records to {}", data.size(), fileName);
        return outputPath;
    }

    /**
     * List all CSV files in the output directory.
     *
     * @return List of CSV file names
     */
    public List<String> listCsvFiles() throws IOException {
        Path outputDir = Paths.get(properties.getOutputDir());

        if (!Files.exists(outputDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(outputDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".csv"))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    /**
     * Get the full path for a CSV file.
     *
     * @param fileName CSV file name
     * @return Full path
     */
    public Path getCsvPath(String fileName) {
        return Paths.get(properties.getOutputDir(), fileName);
    }

    /**
     * Delete a CSV file.
     *
     * @param fileName File name to delete
     * @return true if deleted successfully
     */
    public boolean deleteCsvFile(String fileName) {
        try {
            Path path = getCsvPath(fileName);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a CSV file exists.
     *
     * @param symbol    Stock symbol
     * @param startDate Start date
     * @param endDate   End date
     * @return true if file exists
     */
    public boolean csvExists(String symbol, LocalDate startDate, LocalDate endDate) {
        String safeSymbol = sanitizeSymbol(symbol);
        String fileName = String.format("%s_%s_%s.csv",
                safeSymbol,
                startDate.format(DATE_FORMATTER),
                endDate.format(DATE_FORMATTER));

        return Files.exists(getCsvPath(fileName));
    }

    /**
     * Get output directory path.
     */
    public String getOutputDirectory() {
        return Paths.get(properties.getOutputDir()).toAbsolutePath().toString();
    }

    private String formatCsvLine(OhlcData ohlc) {
        return String.format("%s,%.4f,%.4f,%.4f,%.4f,%s",
                ohlc.getDate().format(DATE_FORMATTER),
                ohlc.getOpen(),
                ohlc.getHigh(),
                ohlc.getLow(),
                ohlc.getClose(),
                ohlc.getVolume() != null ? ohlc.getVolume() : "");
    }

    /**
     * Sanitize symbol for use in filename.
     * Replaces special characters that are invalid in filenames.
     */
    private String sanitizeSymbol(String symbol) {
        // Replace characters that might be problematic in filenames
        return symbol
                .replace("/", "-")
                .replace("\\", "-")
                .replace(":", "-")
                .replace("*", "-")
                .replace("?", "-")
                .replace("\"", "")
                .replace("<", "")
                .replace(">", "")
                .replace("|", "-");
    }
}
