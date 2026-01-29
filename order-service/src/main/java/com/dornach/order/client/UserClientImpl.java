package com.dornach.order.client;

import com.dornach.order.dto.UserResponse;
import com.dornach.order.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Implementation of UserClient using Spring 6 RestClient.
 * Validates user existence by calling user-service.
 */
@Component
public class UserClientImpl implements UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClientImpl.class);
    private final RestClient restClient;

    public UserClientImpl(@Qualifier("userRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user: {}", userId);

        return restClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new UserNotFoundException(userId);
                })
                .body(UserResponse.class);
    }
}
