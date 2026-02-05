
package com.dsip.backend.util;

import com.dsip.backend.config.DsipProperties;
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
}
