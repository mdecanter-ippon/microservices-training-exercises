package com.dornach.order.client;

import java.time.Instant;
import java.util.UUID;

/**
 * Response from user-service when fetching user details.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
