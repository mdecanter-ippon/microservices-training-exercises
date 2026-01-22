package com.dornach.user.dto;

import com.dornach.user.domain.User;
import com.dornach.user.domain.UserRole;
import com.dornach.user.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for user responses.
 *
 * TODO (Step 1 - Exercise 2):
 * Convert this class to a Java Record.
 *
 * Include a static factory method:
 * public static UserResponse from(User user) { ... }
 */
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // TODO: Convert to record
    // Example:
    // public record UserResponse(
    //     UUID id,
    //     String email,
    //     ...
    // ) {
    //     public static UserResponse from(User user) { ... }
    // }

    public UserResponse() {}

    public UserResponse(UUID id, String email, String firstName, String lastName,
                        UserRole role, UserStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

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

    // Getters
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
