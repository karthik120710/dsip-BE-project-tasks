package com.dsip.backend.config;

import com.dsip.backend.enums.DeploymentStyle;
import com.dsip.backend.enums.StockType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "dsip")
public class DsipProperties {

    private int tradingDaysPerYear = 252;
    private int phaseCount = 3;

    public double targetReturnPerPartitionPercentage(StockType stockType) {
        return getTargetReturnPercentageForStockType(stockType);
    }

    private double returnWeight = 0.8;
    private double growthWeight = 0.2;
    private LoadFactor loadFactor = new LoadFactor();

    // Opportunity multiplier configuration
    private double avgDeviationNormalizationFactor = 0.10; // 10% for full signal
    private double lockInNormalizationFactor = 0.08; // 8% for full signal
    private double avgDeviationWeight = 0.65;
    private double lockInWeight = 0.35;

    // Opportunity multiplier caps
    private double opportunityMultiplierMin = 0.7;
    private double opportunityMultiplierMaxNormal = 1.4;
    private double opportunityMultiplierMaxAbnormalDip = 2.0;

    // Contingency multiplier configuration
    private double contingencyMultiplierMax = 1.1;
    private double contingencyMultiplierMin = 0.7;
    private double contingencyDecayFactor = 0.4;

    // Lifecycle thresholds
    private double successProgressThreshold = 0.80; // 80%
    private double successTimeThreshold = 1.0; // 100%
    private double successCapitalThreshold = 0.90; // 90%
    private double killReturnThreshold = -30.0; // -30%
    private double killTimeThreshold = 2.0; // 200%
    private double neutralTimeThreshold = 1.5; // 150%

    /**
     * Target return percentages by stock type.
     * Values are in decimal form (e.g., 0.24 = 24%)
     */
    private static final Map<StockType, Double> TARGET_RETURNS = Map.of(
            StockType.PENNY, 0.24,
            StockType.MIDCAP, 0.21,
            StockType.LARGECAP, 0.15,
            StockType.ETF, 0.15);

    /**
     * Get target return for a stock type.
     *
     * @param stockType the stock type
     * @return target return as decimal (e.g., 0.24 for 24%)
     */
    public double getTargetReturnForStockType(StockType stockType) {
        return TARGET_RETURNS.getOrDefault(stockType, 0.24);
    }

    /**
     * Get target return percentage for a stock type.
     *
     * @param stockType the stock type
     * @return target return as percentage (e.g., 24.0 for 24%)
     */
    public double getTargetReturnPercentageForStockType(StockType stockType) {
        return getTargetReturnForStockType(stockType) * 100;
    }

    @Data
    public static class LoadFactor {
        /**
         * Phase weights for GRADUAL deployment: equal distribution across phases.
         * [Phase1, Phase2, Phase3] weights that sum to ~1.0
         */
        private List<Double> gradual = List.of(0.33, 0.33, 0.33);

        /**
         * Phase weights for MODERATE deployment: front-loaded with gradual reduction.
         */
        private List<Double> moderate = List.of(0.42, 0.33, 0.24);

        /**
         * Phase weights for AGGRESSIVE deployment: heavily front-loaded.
         */
        private List<Double> aggressive = List.of(0.51, 0.24, 0.24);

        public List<Double> getByKey(String key) {
            return switch (key.toLowerCase()) {
                case "gradual" -> gradual;
                case "moderate" -> moderate;
                case "aggressive" -> aggressive;
                default -> gradual; // Default to gradual for safety
            };
        }

        public List<Double> getByDeploymentStyle(DeploymentStyle style) {
            return switch (style) {
                case GRADUAL -> gradual;
                case MODERATE -> moderate;
                case AGGRESSIVE -> aggressive;
            };
        }
    }
}
