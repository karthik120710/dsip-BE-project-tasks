package com.dsip.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a whitelisted email address.
 * Maps to the 'whitelisted_emails' table in the database.
 * Email addresses are normalized to lowercase for consistency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistedEmail {

    /**
     * Unique identifier for the whitelisted email (UUID).
     * Auto-generated if not provided during creation.
     */
    private UUID id;

    /**
     * The whitelisted email address (unique, normalized to lowercase).
     */
    private String email;

    /**
     * Email or username of the person who added this email to the whitelist.
     */
    private String addedBy;

    /**
     * Timestamp when the email was added to the whitelist.
     */
    private Instant createdAt;
}
