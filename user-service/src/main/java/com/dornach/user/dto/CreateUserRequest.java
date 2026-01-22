package com.dornach.user.dto;

import com.dornach.user.domain.UserRole;

/**
 * DTO for creating a new user.
 *
 * TODO (Step 1 - Exercise 2):
 * Convert this class to a Java Record with Bean Validation annotations.
 *
 * Required validations:
 * - email: @NotBlank, @Email
 * - firstName: @NotBlank, @Size(min = 2, max = 50)
 * - lastName: @NotBlank, @Size(min = 2, max = 50)
 * - role: @NotNull
 *
 * Hint: Records are immutable and automatically generate constructor, getters, equals, hashCode, toString.
 */
public class CreateUserRequest {

    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;

    // TODO: Convert to record with validation annotations
    // Example:
    // public record CreateUserRequest(
    //     @NotBlank @Email String email,
    //     ...
    // ) {}

    public CreateUserRequest() {}

    public CreateUserRequest(String email, String firstName, String lastName, UserRole role) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
}
