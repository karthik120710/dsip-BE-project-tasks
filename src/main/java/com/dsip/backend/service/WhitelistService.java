package com.dsip.backend.service;

import com.dsip.backend.mapper.WhitelistedEmailMapper;
import com.dsip.backend.entity.WhitelistedEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for email whitelist management.
 * Handles adding, removing, and querying whitelisted email addresses.
 * All email addresses are normalized to lowercase for consistency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhitelistService {

    private final WhitelistedEmailMapper whitelistedEmailMapper;

    /**
     * Checks if an email address is whitelisted.
     *
     * @param email the email address to check
     * @return true if the email is whitelisted, false otherwise
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional(readOnly = true)
    public boolean isEmailWhitelisted(String email) {
        validateEmail(email);
        String normalizedEmail = email.toLowerCase().trim();
        boolean whitelisted = whitelistedEmailMapper.existsByEmail(normalizedEmail);
        log.debug("Email whitelist check for {}: {}", normalizedEmail, whitelisted);
        return whitelisted;
    }

    /**
     * Retrieves all whitelisted email addresses.
     *
     * @return list of all whitelisted emails
     */
    @Transactional(readOnly = true)
    public List<WhitelistedEmail> getAllWhitelistedEmails() {
        return whitelistedEmailMapper.findAll();
    }

    /**
     * Finds a whitelisted email by its address.
     *
     * @param email the email address to find
     * @return Optional containing the whitelisted email if found, empty otherwise
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional(readOnly = true)
    public Optional<WhitelistedEmail> findByEmail(String email) {
        validateEmail(email);
        String normalizedEmail = email.toLowerCase().trim();
        return whitelistedEmailMapper.findByEmail(normalizedEmail);
    }

    /**
     * Adds an email address to the whitelist.
     * If the email already exists, returns the existing entry.
     *
     * @param email the email address to add
     * @param addedBy the user who added this email
     * @return the whitelisted email entity
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional
    public WhitelistedEmail addEmail(String email, String addedBy) {
        validateEmail(email);
        String normalizedEmail = email.toLowerCase().trim();

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

    /**
     * Removes an email address from the whitelist.
     *
     * @param email the email address to remove
     * @return true if the email was removed, false if it wasn't found
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional
    public boolean removeEmail(String email) {
        validateEmail(email);
        String normalizedEmail = email.toLowerCase().trim();

        if (whitelistedEmailMapper.existsByEmail(normalizedEmail)) {
            whitelistedEmailMapper.deleteByEmail(normalizedEmail);
            log.info("Removed email from whitelist: {}", normalizedEmail);
            return true;
        }
        return false;
    }

    /**
     * Validates that an email address is not null or empty.
     *
     * @param email the email to validate
     * @throws IllegalArgumentException if email is null or empty
     */
    private void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
    }
}
