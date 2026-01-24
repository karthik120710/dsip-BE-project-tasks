package com.dsip.backend.auth;

import com.dsip.backend.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class SessionLifetimeFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;

    private static final String SESSION_CREATED_AT = "SESSION_CREATED_AT";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null) {
            Long createdAt = (Long) session.getAttribute(SESSION_CREATED_AT);

            if (createdAt != null) {
                long maxLifetimeMillis = (long) appProperties.getSession().getMaxLifetimeDays() * 24 * 60 * 60 * 1000;
                long now = Instant.now().toEpochMilli();

                if (now - createdAt > maxLifetimeMillis) {
                    log.info("Session exceeded maximum lifetime, invalidating");
                    session.invalidate();
                    SecurityContextHolder.clearContext();

                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"Session expired\", \"message\": \"Your session has expired. Please log in again.\"}");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
