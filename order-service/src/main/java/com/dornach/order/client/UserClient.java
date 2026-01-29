package com.dornach.order.client;

import com.dornach.order.dto.UserResponse;

import java.util.UUID;

/**
 * Client interface for communicating with user-service.
 * Using an interface allows for easy mocking in tests.
 */
public interface UserClient {
    UserResponse getUserById(UUID userId);
}
