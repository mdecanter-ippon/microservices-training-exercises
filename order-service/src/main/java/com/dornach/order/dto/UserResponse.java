package com.dornach.order.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a user from user-service.
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
