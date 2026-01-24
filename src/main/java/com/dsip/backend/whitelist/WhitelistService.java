package com.dsip.backend.whitelist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhitelistService {

    private final WhitelistedEmailRepository whitelistedEmailRepository;

    @Transactional(readOnly = true)
    public boolean isEmailWhitelisted(String email) {
        boolean whitelisted = whitelistedEmailRepository.existsByEmail(email.toLowerCase());
        log.debug("Email whitelist check for {}: {}", email, whitelisted);
        return whitelisted;
    }

    @Transactional(readOnly = true)
    public List<WhitelistedEmail> getAllWhitelistedEmails() {
        return whitelistedEmailRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<WhitelistedEmail> findByEmail(String email) {
        return whitelistedEmailRepository.findByEmail(email.toLowerCase());
    }

    @Transactional
    public WhitelistedEmail addEmail(String email, String addedBy) {
        String normalizedEmail = email.toLowerCase();

        return whitelistedEmailRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> {
                    WhitelistedEmail whitelistedEmail = WhitelistedEmail.builder()
                            .email(normalizedEmail)
                            .addedBy(addedBy)
                            .build();
                    log.info("Added email to whitelist: {} by {}", normalizedEmail, addedBy);
                    return whitelistedEmailRepository.save(whitelistedEmail);
                });
    }

    @Transactional
    public boolean removeEmail(String email) {
        String normalizedEmail = email.toLowerCase();

        if (whitelistedEmailRepository.existsByEmail(normalizedEmail)) {
            whitelistedEmailRepository.deleteByEmail(normalizedEmail);
            log.info("Removed email from whitelist: {}", normalizedEmail);
            return true;
        }
        return false;
    }
}
