package com.dornach.user.dto;

import com.dornach.user.domain.User;
import com.dornach.user.domain.UserRole;
import com.dornach.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing a user.
 * Factory method provides clean conversion from entity.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
