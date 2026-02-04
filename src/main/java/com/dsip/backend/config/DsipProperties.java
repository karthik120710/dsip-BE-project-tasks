package com.dsip.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "dsip")
public class DsipProperties {

    private int tradingDaysPerYear;
    private int phaseCount;
    private LoadFactor loadFactor = new LoadFactor();

    @Data
    public static class LoadFactor {
        private List<Double> gradual = List.of(0.5, 1.0, 1.5);
        private List<Double> moderate = List.of(0.8, 1.0, 1.2);
        private List<Double> aggressive = List.of(1.0, 1.0, 1.0);

        public List<Double> getByKey(String key) {
            return switch (key.toLowerCase()) {
                case "gradual" -> gradual;
                case "moderate" -> moderate;
                case "aggressive" -> aggressive;
                default -> List.of(1.0, 1.0, 1.0);
            };
        }
    }
}
