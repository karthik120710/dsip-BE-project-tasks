package com.dsip.backend.simulation;

import com.dsip.backend.data.DataGeneratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting detailed simulation results to CSV files.
 * Includes all intermediate calculations for verification purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationCsvExporter {

    private final DataGeneratorProperties properties;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Export simulation results to a comprehensive CSV file.
     *
     * @param result     Simulation result to export
     * @param symbol     Stock symbol (for filename)
     * @return Path to the created CSV file
     */
    public Path exportSimulation(SimulationResult result, String symbol) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String fileName = String.format("%s_simulation_%s.csv", sanitizeSymbol(symbol), timestamp);
        Path outputPath = Paths.get(properties.getOutputDir(), fileName);

        // Ensure directory exists
        Files.createDirectories(outputPath.getParent());

        log.info("Exporting simulation results to {}", outputPath.toAbsolutePath());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write configuration header
            writeConfigurationSection(writer, result);

            // Write summary section
            writeSummarySection(writer, result);

            // Write partition summary section
            writePartitionSummarySection(writer, result);

            // Write formula reference section
            writeFormulaReferenceSection(writer);

            // Write daily data header
            writeDailyDataHeader(writer);

            // Write daily data rows
            for (SimulationResult.DayResult day : result.getDayResults()) {
                writeDayRow(writer, day);
            }
        }

        log.info("Successfully exported simulation with {} days to {}",
                result.getDayResults().size(), fileName);
        return outputPath;
    }

    private void writeConfigurationSection(BufferedWriter writer, SimulationResult result) throws IOException {
        SimulationConfig config = result.getConfig();

        writer.write("# DSIP SIMULATION RESULTS");
        writer.newLine();
        writer.write("# Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        writer.newLine();
        writer.newLine();

        writer.write("## CONFIGURATION");
        writer.newLine();
        writer.write(String.format("Total Capital,$%.2f", config.getTotalCapital()));
        writer.newLine();
        writer.write(String.format("Conviction Period,%.2f years", config.getConvictionPeriodYears()));
        writer.newLine();
        writer.write(String.format("Stock Type,%s", config.getStockType()));
        writer.newLine();
        writer.write(String.format("Target Return,%.2f%%", config.getStockType().getTargetReturnPercentage() * 100));
        writer.newLine();
        writer.write(String.format("Deployment Style,%s", config.getDeploymentStyle()));
        writer.newLine();
        writer.write(String.format("Initial Price,$%.4f", config.getInitialPrice()));
        writer.newLine();
        writer.write(String.format("Conviction Score,%d", config.getBaseConvictionScore()));
        writer.newLine();
        writer.write(String.format("Expected Partition Days,%d", config.getExpectedPartitionDays()));
        writer.newLine();
        writer.write(String.format("Max Partitions,%d", config.getMaxPartitions()));
        writer.newLine();
        writer.write(String.format("Execution Price Type,%s", config.getExecutionPriceType()));
        writer.newLine();
        writer.write(String.format("Lock-In Reference Type,%s", config.getLockInReferenceType()));
        writer.newLine();
        writer.newLine();
    }

    private void writeSummarySection(BufferedWriter writer, SimulationResult result) throws IOException {
        writer.write("## OVERALL RESULTS");
        writer.newLine();
        writer.write(String.format("Days Simulated,%d", result.getTotalDaysSimulated()));
        writer.newLine();
        writer.write(String.format("Partitions Created,%d", result.getPartitionsCreated()));
        writer.newLine();
        writer.write(String.format("Successful Partitions,%d", result.getSuccessfulPartitions()));
        writer.newLine();
        writer.write(String.format("Killed Partitions,%d", result.getKilledPartitions()));
        writer.newLine();
        writer.write(String.format("Neutral Partitions,%d", result.getNeutralPartitions()));
        writer.newLine();
        writer.write(String.format("Total Capital Invested,$%.2f", result.getTotalCapitalInvested()));
        writer.newLine();
        writer.write(String.format("Total Shares Accumulated,%.6f", result.getTotalSharesAccumulated()));
        writer.newLine();
        writer.write(String.format("Average Price Paid,$%.4f", result.getAveragePricePaid()));
        writer.newLine();
        writer.write(String.format("Final Market Price,$%.4f", result.getFinalMarketPrice()));
        writer.newLine();
        writer.write(String.format("Final Portfolio Value,$%.2f", result.getFinalPortfolioValue()));
        writer.newLine();
        writer.write(String.format("Overall Return,%.2f%%", result.getOverallReturnPct()));
        writer.newLine();
        writer.newLine();
    }

    private void writePartitionSummarySection(BufferedWriter writer, SimulationResult result) throws IOException {
        writer.write("## PARTITION SUMMARY");
        writer.newLine();
        writer.write("Partition,Status,Capital Allocated,Capital Invested,Shares,Avg Price,Days,Growth Days,Return %,Time Progress %,Capital Progress %,Partition Progress %");
        writer.newLine();

        for (SimulationResult.PartitionResult pr : result.getPartitionResults()) {
            writer.write(String.format("%d,%s,$%.2f,$%.2f,%.6f,$%.4f,%d,%d,%.2f%%,%.2f%%,%.2f%%,%.2f%%",
                    pr.getPartitionIndex(),
                    pr.getEndReason(),
                    pr.getCapitalAllocated(),
                    pr.getCapitalInvested(),
                    pr.getSharesAccumulated(),
                    pr.getAvgPrice(),
                    pr.getDaysActive(),
                    pr.getSuccessfulGrowthDays(),
                    pr.getCumulativeReturnPct(),
                    pr.getTimeProgressPct(),
                    pr.getCapitalProgressPct(),
                    pr.getPartitionProgressPct()));
            writer.newLine();
        }
        writer.newLine();
    }

    private void writeFormulaReferenceSection(BufferedWriter writer) throws IOException {
        writer.write("## FORMULA REFERENCE");
        writer.newLine();
        writer.write("# AvgHoldingPrice = PartitionCapitalInvested / PartitionSharesBought");
        writer.newLine();
        writer.write("# AvgDeviationPct = (AvgHoldingPrice - ExecutionPrice) / AvgHoldingPrice * 100");
        writer.newLine();
        writer.write("# AvgSignal = clamp(AvgDeviationPct / 10, -1, 1)");
        writer.newLine();
        writer.write("# LockInPct = (ExecutionPrice - LockInRefPrice) / LockInRefPrice * 100");
        writer.newLine();
        writer.write("# LockInSignal = clamp(LockInPct / 8, -1, 1)");
        writer.newLine();
        writer.write("# RawOpportunitySignal = 0.65 * AvgSignal - 0.35 * LockInSignal");
        writer.newLine();
        writer.write("# ConvictionAmplifier = 0.6 + (ConvictionScore / 100) * 0.6");
        writer.newLine();
        writer.write("# OpportunityMultiplier = clamp(1 + RawOpportunitySignal * ConvictionAmplifier, 0.7, 1.4 or 2.0 if abnormal dip)");
        writer.newLine();
        writer.write("# ReturnProgress = CumulativeReturnPct / TargetReturnPct");
        writer.newLine();
        writer.write("# GrowthProgress = GrowthDayCount / ExpectedPartitionDays");
        writer.newLine();
        writer.write("# PartitionProgress = 0.8 * ReturnProgress + 0.2 * GrowthProgress");
        writer.newLine();
        writer.write("# ContingencyMultiplier = clamp(1.1 - PartitionProgress * 0.4, 0.7, 1.1)");
        writer.newLine();
        writer.write("# NeutralCapital = PartitionCapitalAllocated / ExpectedPartitionDays");
        writer.newLine();
        writer.write("# FinalMultiplier = OpportunityMultiplier * ContingencyMultiplier");
        writer.newLine();
        writer.write("# RecommendedAmount = NeutralCapital * FinalMultiplier");
        writer.newLine();
        writer.write("# ExecutedAmount = min(RecommendedAmount, RemainingCapital)");
        writer.newLine();
        writer.write("# SharesAcquired = ExecutedAmount / ExecutionPrice");
        writer.newLine();
        writer.write("# CumulativeReturnPct = (ClosePrice - AvgHoldingPrice) / AvgHoldingPrice * 100");
        writer.newLine();
        writer.write("# IsGrowthDay = (ClosePrice > PrevClosePrice) AND (CumulativeReturnPct > 0)");
        writer.newLine();
        writer.newLine();
    }

    private void writeDailyDataHeader(BufferedWriter writer) throws IOException {
        writer.write("## DAILY CALCULATION LOG");
        writer.newLine();

        // Write header row with all columns
        String[] headers = {
                // Basic Info
                "Day", "Date", "Partition",
                // Price Data
                "Open", "High", "Low", "Close", "ExecPrice", "LockInRefPrice",
                // Price Changes
                "PriceChange%", "LockIn%",
                // Opportunity Multiplier Breakdown
                "AvgHoldingPrice", "AvgDeviation%", "AvgSignal", "LockInSignal",
                "RawOppSignal", "ConvictionAmp", "AbnormalDip", "OppMultiplier",
                // Contingency Multiplier Breakdown
                "ReturnProgress", "GrowthProgress", "PartProgress", "ContMultiplier",
                // Investment Calculation
                "NeutralCapital", "FinalMultiplier", "RecommendedAmt", "ExecutedAmt", "SharesAcquired",
                // Partition Running Totals
                "PartCapInvested", "PartSharesBought", "PartRemaining",
                // Tracker Running Totals
                "TotalCapInvested", "TotalSharesHeld", "PortfolioValue",
                // Performance
                "CumReturn%", "IsGrowthDay", "GrowthDayCount",
                // Progress
                "TimeProgress%", "CapitalProgress%", "PartitionProgress%",
                // Partition End
                "PartitionEndReason",
                // Notes
                "Notes"
        };

        writer.write(String.join(",", headers));
        writer.newLine();
    }

    private void writeDayRow(BufferedWriter writer, SimulationResult.DayResult day) throws IOException {
        StringBuilder row = new StringBuilder();

        // Basic Info
        row.append(day.getDayNumber()).append(",");
        row.append(day.getDate() != null ? day.getDate().format(DATE_FORMATTER) : "").append(",");
        row.append(day.getPartitionIndex()).append(",");

        // Price Data
        row.append(formatDouble(day.getOpenPrice())).append(",");
        row.append(day.getHighPrice() != null ? formatDouble(day.getHighPrice()) : "").append(",");
        row.append(day.getLowPrice() != null ? formatDouble(day.getLowPrice()) : "").append(",");
        row.append(formatDouble(day.getClosePrice())).append(",");
        row.append(formatDouble(day.getExecutionPrice())).append(",");
        row.append(formatDouble(day.getLockInReferencePrice())).append(",");

        // Price Changes
        row.append(formatDouble(day.getPriceChangePct())).append(",");
        row.append(formatDouble(day.getLockInPct())).append(",");

        // Opportunity Multiplier Breakdown
        row.append(formatDouble(day.getAvgHoldingPrice())).append(",");
        row.append(formatDouble(day.getAvgDeviationPct())).append(",");
        row.append(formatDouble(day.getAvgSignal())).append(",");
        row.append(formatDouble(day.getLockInSignal())).append(",");
        row.append(formatDouble(day.getRawOpportunitySignal())).append(",");
        row.append(formatDouble(day.getConvictionAmplifier())).append(",");
        row.append(day.isAbnormalDip() ? "YES" : "NO").append(",");
        row.append(formatDouble(day.getOpportunityMultiplier())).append(",");

        // Contingency Multiplier Breakdown
        row.append(formatDouble(day.getReturnProgress())).append(",");
        row.append(formatDouble(day.getGrowthProgress())).append(",");
        row.append(formatDouble(day.getPartitionProgress())).append(",");
        row.append(formatDouble(day.getContingencyMultiplier())).append(",");

        // Investment Calculation
        row.append(formatDouble(day.getNeutralCapital())).append(",");
        row.append(formatDouble(day.getFinalMultiplier())).append(",");
        row.append(formatDouble(day.getRecommendedAmount())).append(",");
        row.append(formatDouble(day.getExecutedAmount())).append(",");
        row.append(formatDouble(day.getSharesAcquired())).append(",");

        // Partition Running Totals
        row.append(formatDouble(day.getPartitionCapitalInvested())).append(",");
        row.append(formatDouble(day.getPartitionSharesBought())).append(",");
        row.append(formatDouble(day.getPartitionRemainingCapital())).append(",");

        // Tracker Running Totals
        row.append(formatDouble(day.getTotalCapitalInvested())).append(",");
        row.append(formatDouble(day.getTotalSharesHeld())).append(",");
        row.append(formatDouble(day.getPortfolioValue())).append(",");

        // Performance
        row.append(formatDouble(day.getCumulativeReturnPct())).append(",");
        row.append(day.isGrowthDay() ? "YES" : "NO").append(",");
        row.append(day.getGrowthDayCount()).append(",");

        // Progress
        row.append(formatDouble(day.getTimeProgressPct())).append(",");
        row.append(formatDouble(day.getCapitalProgressPct())).append(",");
        row.append(formatDouble(day.getPartitionProgressPct())).append(",");

        // Partition End
        row.append(day.getPartitionEndReason() != null ? day.getPartitionEndReason() : "").append(",");

        // Notes
        row.append(day.getNotes() != null ? escapecsv(day.getNotes()) : "");

        writer.write(row.toString());
        writer.newLine();
    }

    private String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        // Use more precision for small values, less for large
        if (Math.abs(value) < 0.01 && value != 0) {
            return String.format("%.6f", value);
        } else if (Math.abs(value) < 1) {
            return String.format("%.4f", value);
        } else {
            return String.format("%.2f", value);
        }
    }

    private String escapecsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String sanitizeSymbol(String symbol) {
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
