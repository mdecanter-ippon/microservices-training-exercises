package com.dornach.order.client;

import com.dornach.order.exception.UserNotFoundException;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Client for communicating with the user-service.
 * Validates user existence before creating orders.
 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient userRestClient;

    public UserClient(RestClient userRestClient) {
        this.userRestClient = userRestClient;
    }

    /**
     * Validates that a user exists by fetching their details from user-service.
     * This demonstrates synchronous inter-service communication for validation.
     *
     * @param userId the user ID to validate
     * @return the user response from user-service
     * @throws UserNotFoundException if the user does not exist
     */
    @Retry(name = "userService")
    public UserResponse getUserById(UUID userId) {
        log.info("Validating user exists: {}", userId);

        try {
            UserResponse response = userRestClient.get()
                    .uri("/users/{id}", userId)
                    .retrieve()
                    .body(UserResponse.class);

            log.info("User validated: {}", userId);
            return response;

        } catch (HttpClientErrorException.NotFound e) {
            log.error("User not found: {}", userId);
            throw new UserNotFoundException(userId);
        }
    }
}
