package com.dornach.order.client;

import com.dornach.order.dto.UserResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Implementation of UserClient using RestClient.
 *
 * TODO (Step 2 - Exercise 2): Implement the client
 *
 * 1. Use the injected RestClient to GET /users/{userId}
 * 2. Return the UserResponse
 * 3. Handle 404 errors (throw UserNotFoundException)
 *
 * TODO (Step 2 - Exercise 3): Add Resilience4j annotations
 * - @Retry(name = "userService", fallbackMethod = "getUserByIdFallback")
 */
@Component
public class UserClientImpl implements UserClient {

    private static final Logger log = Logger.getLogger(UserClientImpl.class.getName());

    private final RestClient restClient;

    public UserClientImpl(@Qualifier("userRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user: " + userId);

        // TODO (Step 2): Implement using RestClient
        // Hint:
        // return restClient.get()
        //     .uri("/users/{id}", userId)
        //     .retrieve()
        //     .onStatus(status -> status.value() == 404, (request, response) -> {
        //         throw new UserNotFoundException(userId);
        //     })
        //     .body(UserResponse.class);

        throw new UnsupportedOperationException("TODO: Implement getUserById");
    }

    // TODO (Step 2 - Exercise 3): Add fallback method
    // private UserResponse getUserByIdFallback(UUID userId, Exception ex) {
    //     log.severe("Failed to fetch user " + userId + " after retries: " + ex.getMessage());
    //     throw new ServiceUnavailableException("User service is unavailable");
    // }
}
