package com.dsip.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Admin-API-Key";

    private final AppProperties appProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        String expectedKey = appProperties.getAdmin().getApiKey();

        if (expectedKey == null || !expectedKey.equals(apiKey)) {
            log.warn("Unauthorized admin access attempt to {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \"Invalid or missing admin API key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Protect all /api/admin/** endpoints
        if (path.startsWith("/api/admin/")) {
            return false;
        }

        // Protect DELETE /api/stocks/**
        if (path.startsWith("/api/stocks/") && "DELETE".equalsIgnoreCase(method)) {
            return false;
        }

        // All other requests skip this filter
        return true;
    }
}
