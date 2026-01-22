package com.dornach.user.dto;

import com.dornach.user.domain.UserRole;
import com.dornach.user.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for user updates.
 * All fields are optional for partial updates.
 */
public record UpdateUserRequest(
        @Email(message = "Email must be valid")
        String email,

        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        String firstName,

        @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
        String lastName,

        UserRole role,

        UserStatus status
) {}
