package com.dornach.order.exception;

import java.util.UUID;

/**
 * Exception thrown when a user is not found in user-service.
 */
public class UserNotFoundException extends RuntimeException {

    private final UUID userId;

    public UserNotFoundException(UUID userId) {
        super(String.format("User not found with id: %s", userId));
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
