package com.dsip.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a user in the system.
 * Maps to the 'users' table in the database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Unique identifier for the user (UUID).
     * Auto-generated if not provided during creation.
     */
    private UUID id;

    /**
     * User's email address (unique).
     */
    private String email;

    /**
     * User's display name.
     */
    private String name;

    /**
     * URL to the user's profile picture.
     */
    private String profilePicture;

    /**
     * Timestamp when the user was created.
     */
    private Instant createdAt;
}
