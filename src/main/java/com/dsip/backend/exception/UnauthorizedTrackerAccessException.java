package com.dsip.backend.exception;

import java.util.UUID;

/**
 * Exception thrown when a user attempts to access a tracker they don't own.
 */
public class UnauthorizedTrackerAccessException extends DsipException {

    public UnauthorizedTrackerAccessException(Integer trackerId, UUID userId) {
        super(String.format("User %s is not authorized to access tracker %d", userId, trackerId));
    }

    public UnauthorizedTrackerAccessException(String message) {
        super(message);
    }
}
