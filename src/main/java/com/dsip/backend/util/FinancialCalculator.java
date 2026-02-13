
package com.dsip.backend.util;

import com.dsip.backend.config.DsipProperties;
import com.dsip.backend.enums.StockType;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FinancialCalculator {

    private final DsipProperties dsipProperties;
    private static final int DAYS_IN_YEAR = 365;

    public int getTradingDaysPerYear() {
        return dsipProperties.getTradingDaysPerYear();
    }

    public Double calculateProfitPercentage(double currentValue, double investedCapital) {
        if (investedCapital <= 0) {
            return null;
        }
        return ((currentValue - investedCapital) / investedCapital) * 100;
    }

    public double calculateMarketValue(double currentPrice, int quantity) {
        return currentPrice * quantity;
    }

    public double calculateMarketValue(double currentPrice, Double quantity) {
        if (quantity == null)
            return 0.0;
        return currentPrice * quantity;
    }

    public double round(double value, int places) {
        if (places < 0)
            throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public int calculateDaysBetween(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0; // Or throw exception based on requirement
        }
        LocalDate startDate = start.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = end.atZone(ZoneId.systemDefault()).toLocalDate();
        long calendarDays = ChronoUnit.DAYS.between(startDate, endDate);
        return (int) (calendarDays * dsipProperties.getTradingDaysPerYear() / DAYS_IN_YEAR);
    }

    public int calculateMedian(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        Collections.sort(values);
        int size = values.size();
        if (size % 2 == 1) {
            return values.get(size / 2);
        } else {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2;
        }
    }

    public double calculateSharesBought(Double executionAmount, Double executionPrice) {
        if (executionPrice == null || executionPrice <= 0 || executionAmount == null) {
            return 0.0;
        }
        return executionAmount / executionPrice;
    }

    public double calculateAverageCost(com.dsip.backend.entity.DsipPartition partition) {
        if (partition == null || partition.getNoOfSharesBought() == null || partition.getNoOfSharesBought() == 0
                || partition.getCapitalInvestedSoFar() == null) {
            return 0.0;
        }
        return partition.getCapitalInvestedSoFar() / partition.getNoOfSharesBought();
    }

    public double cumulativeReturnPercentage(double shares, double amount, double marketPrice) {
        double amountForOneShare = amount / shares;
        return calculateProfitPercentage(marketPrice, amountForOneShare);
    }

    public boolean calculateIsGrowth(com.dsip.backend.entity.DsipPartition partition, Double currentExecutionPrice,
            Double marketPrice) {
        if (partition.getCapitalInvestedSoFar() == null ||
                partition.getCapitalInvestedSoFar() == 0.0 ||
                partition.getNoOfSharesBought() == null ||
                partition.getNoOfSharesBought() == 0.0) {
            return false;
        }

        if (marketPrice == null || marketPrice <= 0) {
            return false;
        }
        double currentCumulativeReturnPercentage = cumulativeReturnPercentage(partition.getNoOfSharesBought(),
                partition.getCapitalInvestedSoFar(), marketPrice);
        // Logic: Momentum (Current > Last) AND Profitability
        return (currentExecutionPrice > marketPrice) && currentCumulativeReturnPercentage > 0;
    }

    public double calculatePartitionProgressPercentage(com.dsip.backend.entity.DsipPartition partition,
            double marketPrice, StockType stockType) {
        double returnProgress = calculateReturnProgressPercentage(partition, marketPrice, stockType);
        double growthProgress = calculateGrowthPersistencePercentage(partition) / 100.0;
        return (0.8 * returnProgress) + (0.2 * growthProgress);
    }

    public double calculateTimeProgressPercentage(com.dsip.backend.entity.DsipPartition partition) {
        double daysElapsed = this.calculateDaysBetween(partition.getCreatedAt(), Instant.now());
        return (daysElapsed / partition.getExpectedPartitionDays()) * 100;
    }

    public double calculateCapitalProgressPercentage(com.dsip.backend.entity.DsipPartition partition) {
        return (partition.getCapitalInvestedSoFar() / partition.getPartitionCapitalAllocated()) * 100;
    }

    public double calculateReturnProgressPercentage(com.dsip.backend.entity.DsipPartition partition, double marketPrice,
            StockType stockType) {
        if (partition.getCapitalInvestedSoFar() == null ||
                partition.getCapitalInvestedSoFar() == 0.0 ||
                partition.getNoOfSharesBought() == null ||
                partition.getNoOfSharesBought() == 0.0) {
            return 0.0;
        }
        double currentCumulativeReturnPercentage = cumulativeReturnPercentage(partition.getNoOfSharesBought(),
                partition.getCapitalInvestedSoFar(), marketPrice);

        return currentCumulativeReturnPercentage / dsipProperties.targetReturnPerPartitionPercentage(stockType);
    }

    public double calculateGrowthPersistencePercentage(com.dsip.backend.entity.DsipPartition partition) {
        if (partition.getSuccessfulGrowthCount() == null || partition.getExpectedPartitionDays() == null
                || partition.getExpectedPartitionDays() == 0) {
            return 0.0;
        }
        return ((double) partition.getSuccessfulGrowthCount() / partition.getExpectedPartitionDays()) * 100;
    }

}
