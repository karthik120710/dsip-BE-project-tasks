package com.dsip.backend.auth;

import com.dsip.backend.config.AppProperties;
import com.dsip.backend.user.UserService;
import com.dsip.backend.whitelist.WhitelistService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final WhitelistService whitelistService;
    private final UserService userService;
    private final AppProperties appProperties;

    private static final String SESSION_CREATED_AT = "SESSION_CREATED_AT";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            log.error("Unexpected authentication type: {}", authentication.getClass());
            response.sendRedirect(appProperties.getOauth2().getFailureRedirectUrl());
            return;
        }

        OAuth2User oauth2User = oauthToken.getPrincipal();
        String email = oauth2User.getAttribute("email");

        if (email == null || email.isBlank()) {
            log.error("Email not provided by OAuth2 provider");
            invalidateSessionAndRedirect(request, response, appProperties.getOauth2().getFailureRedirectUrl());
            return;
        }

        email = email.toLowerCase();

        // Check whitelist
        if (!whitelistService.isEmailWhitelisted(email)) {
            log.warn("Access denied for non-whitelisted email: {}", email);
            invalidateSessionAndRedirect(request, response, appProperties.getOauth2().getWhitelistFailureRedirectUrl());
            return;
        }

        // Extract user info from Google
        String name = oauth2User.getAttribute("name");
        String profilePicture = oauth2User.getAttribute("picture");

        // Create or update user
        userService.createOrUpdateUser(email, name, profilePicture);

        // Set session creation time for absolute lifetime tracking
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SESSION_CREATED_AT, Instant.now().toEpochMilli());

            // Set inactivity timeout (in seconds)
            int inactivityTimeoutSeconds = appProperties.getSession().getInactivityTimeoutDays() * 24 * 60 * 60;
            session.setMaxInactiveInterval(inactivityTimeoutSeconds);
        }

        log.info("Successful authentication for user: {}", email);
        response.sendRedirect(appProperties.getOauth2().getSuccessRedirectUrl());
    }

    private void invalidateSessionAndRedirect(HttpServletRequest request,
                                              HttpServletResponse response,
                                              String redirectUrl) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(redirectUrl);
    }
}
