package com.dsip.backend.service;

import com.dsip.backend.mapper.UserMapper;
import com.dsip.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * Service layer for user management operations.
 * Handles user creation, updates, and queries using MyBatis mappers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserMapper userMapper;

    /**
     * Finds a user by their email address.
     *
     * @param email the email address to search for
     * @return Optional containing the user if found, empty otherwise
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        validateEmail(email);
        return userMapper.findByEmail(email);
    }

    /**
     * Creates a new user or updates an existing user's information.
     * If a user with the given email exists, their name and profile picture are updated.
     * Otherwise, a new user is created.
     *
     * @param email the user's email address
     * @param name the user's display name
     * @param profilePicture the URL to the user's profile picture
     * @return the created or updated User entity
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional
    public User createOrUpdateUser(String email, String name, String profilePicture) {
        validateEmail(email);

        return userMapper.findByEmail(email)
                .map(existingUser -> {
                    existingUser.setName(name);
                    existingUser.setProfilePicture(profilePicture);
                    userMapper.update(existingUser);
                    log.info("Updated existing user: {}", email);
                    return existingUser;
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .profilePicture(profilePicture)
                            .createdAt(Instant.now())
                            .build();
                    userMapper.insert(newUser);
                    log.info("Created new user: {}", email);
                    return newUser;
                });
    }

    /**
     * Checks if a user exists with the given email address.
     *
     * @param email the email address to check
     * @return true if a user exists with this email, false otherwise
     * @throws IllegalArgumentException if email is null or empty
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        validateEmail(email);
        return userMapper.existsByEmail(email);
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
