package com.dsip.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private OAuth2Properties oauth2 = new OAuth2Properties();
    private SessionProperties session = new SessionProperties();
    private CorsProperties cors = new CorsProperties();
    private FinnhubProperties finnhub = new FinnhubProperties();
    private UpstoxProperties upstox = new UpstoxProperties();
    private AdminProperties admin = new AdminProperties();

    @Data
    public static class OAuth2Properties {
      @Value("${app.oauth2.success-redirect-url}")
      private String successRedirectUrl;

      @Value("${app.oauth2.failure-redirect-url}")
      private String failureRedirectUrl;

      @Value("${app.oauth2.whitelist-failure-redirect-url}")
      private String whitelistFailureRedirectUrl;
    }

    @Data
    public static class SessionProperties {
        private int inactivityTimeoutDays = 7;
        private int maxLifetimeDays = 60;
    }

    @Data
    public static class CorsProperties {
        private String allowedOrigins = "http://localhost:3000";
    }

    @Data
    public static class FinnhubProperties {
        private List<String> apiKeys = new ArrayList<>();
    }

    @Data
    public static class UpstoxProperties {
        @Value("${app.upstox.access-token}")
        private String accessToken;
    }

    @Data
    public static class AdminProperties {
        @Value("${app.admin.api-key}")
        private String apiKey;
    }
}
