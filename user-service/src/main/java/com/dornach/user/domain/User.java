package com.dornach.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * User entity representing a Dornach employee.
 *
 * TODO (Step 1 - Exercise 1):
 * 1. Add JPA annotations (@Entity, @Table, @Id, etc.)
 * 2. Add @GeneratedValue for UUID generation
 * 3. Add @Enumerated for the role field
 * 4. Add @CreationTimestamp and @UpdateTimestamp for timestamps
 */
public class User {

    // TODO: Add @Id and @GeneratedValue annotations
    private UUID id;

    // TODO: Add @Column with unique constraint
    private String email;

    private String firstName;

    private String lastName;

    // TODO: Add @Enumerated annotation
    private UserRole role;

    // TODO: Add @Enumerated annotation
    private UserStatus status = UserStatus.ACTIVE;

    // TODO: Add @CreationTimestamp annotation
    private Instant createdAt;

    // TODO: Add @UpdateTimestamp annotation
    private Instant updatedAt;

    // Default constructor for JPA
    protected User() {}

    public User(String email, String firstName, String lastName, UserRole role) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
