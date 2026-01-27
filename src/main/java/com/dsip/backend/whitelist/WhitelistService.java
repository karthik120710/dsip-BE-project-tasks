package com.dsip.backend.whitelist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhitelistService {

    private final WhitelistedEmailMapper whitelistedEmailMapper;

    @Transactional(readOnly = true)
    public boolean isEmailWhitelisted(String email) {
        boolean whitelisted = whitelistedEmailMapper.existsByEmail(email.toLowerCase());
        log.debug("Email whitelist check for {}: {}", email, whitelisted);
        return whitelisted;
    }

    @Transactional(readOnly = true)
    public List<WhitelistedEmail> getAllWhitelistedEmails() {
        return whitelistedEmailMapper.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<WhitelistedEmail> findByEmail(String email) {
        return whitelistedEmailMapper.findByEmail(email.toLowerCase());
    }

    @Transactional
    public WhitelistedEmail addEmail(String email, String addedBy) {
        String normalizedEmail = email.toLowerCase();

        return whitelistedEmailMapper.findByEmail(normalizedEmail)
                .orElseGet(() -> {
                    WhitelistedEmail whitelistedEmail = WhitelistedEmail.builder()
                            .email(normalizedEmail)
                            .addedBy(addedBy)
                            .createdAt(Instant.now())
                            .build();
                    whitelistedEmailMapper.insert(whitelistedEmail);
                    log.info("Added email to whitelist: {} by {}", normalizedEmail, addedBy);
                    return whitelistedEmail;
                });
    }

    @Transactional
    public boolean removeEmail(String email) {
        String normalizedEmail = email.toLowerCase();

        if (whitelistedEmailMapper.existsByEmail(normalizedEmail)) {
            whitelistedEmailMapper.deleteByEmail(normalizedEmail);
            log.info("Removed email from whitelist: {}", normalizedEmail);
            return true;
        }
        return false;
    }
}
