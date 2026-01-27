package com.dsip.backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Transactional
    public User createOrUpdateUser(String email, String name, String profilePicture) {
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

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }
}
