package com.dornach.order.client;

import com.dornach.order.dto.UserResponse;

import java.util.UUID;

/**
 * Client interface for calling user-service.
 *
 * TODO (Step 2 - Exercise 2): Define the interface
 *
 * Why use an interface?
 * 1. Testability - Easy to mock in unit tests
 * 2. Decoupling - Service doesn't depend on HTTP implementation
 * 3. Flexibility - Can switch to gRPC, message queue, etc.
 */
public interface UserClient {

    /**
     * Get a user by ID.
     *
     * @param userId the user's UUID
     * @return the user details
     * @throws UserNotFoundException if the user doesn't exist
     */
    UserResponse getUserById(UUID userId);
}
