package com.dsip.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private OAuth2Properties oauth2 = new OAuth2Properties();
    private SessionProperties session = new SessionProperties();
    private CorsProperties cors = new CorsProperties();

    @Data
    public static class OAuth2Properties {
        private String successRedirectUrl = "http://localhost:3000";
        private String failureRedirectUrl = "http://localhost:3000/auth/error";
        private String whitelistFailureRedirectUrl = "http://localhost:3000/auth/unauthorized";
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
}
