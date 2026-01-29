package com.dsip.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void invalidateAllSessionsForUser(String principalName) {
        Map<String, ? extends Session> sessions = sessionRepository
                .findByPrincipalName(principalName);

        sessions.keySet().forEach(sessionId -> {
            sessionRepository.deleteById(sessionId);
            log.info("Invalidated session {} for user {}", sessionId, principalName);
        });

        log.info("Invalidated {} sessions for user {}", sessions.size(), principalName);
    }

    @Transactional
    public int cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        int deleted = jdbcTemplate.update(
                "DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < ?",
                now
        );
        log.info("Cleaned up {} expired sessions", deleted);
        return deleted;
    }

    @Transactional(readOnly = true)
    public int countActiveSessionsForUser(String principalName) {
        Map<String, ? extends Session> sessions = sessionRepository
                .findByPrincipalName(principalName);
        return sessions.size();
    }
}
