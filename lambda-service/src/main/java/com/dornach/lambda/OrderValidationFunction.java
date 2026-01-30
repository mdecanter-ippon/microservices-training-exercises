package com.dornach.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class OrderValidationFunction implements Function<OrderValidationRequest, OrderValidationResponse> {

    private static final Logger log = LoggerFactory.getLogger(OrderValidationFunction.class);
    private final RestClient userServiceClient;

    public OrderValidationFunction(
            @Value("${user.service.url:http://localhost:8081}") String userServiceUrl) {
        this.userServiceClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    @Override
    public OrderValidationResponse apply(OrderValidationRequest request) {
        log.info("Validating order for user: {}", request.userId());

        List<String> errors = new ArrayList<>();

        // Validate quantity
        if (request.quantity() == null || request.quantity() <= 0) {
            errors.add("Quantity must be greater than 0");
        }

        // Validate total price
        if (request.totalPrice() == null || request.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Total price must be greater than 0");
        }

        // Validate user exists (call user-service)
        if (request.userId() != null) {
            try {
                var response = userServiceClient.get()
                        .uri("/users/{id}", request.userId())
                        .retrieve()
                        .toBodilessEntity();

                log.info("User {} found", request.userId());
            } catch (RestClientException e) {
                log.warn("User {} not found: {}", request.userId(), e.getMessage());
                errors.add("User not found: " + request.userId());
            }
        } else {
            errors.add("User ID is required");
        }

        boolean valid = errors.isEmpty();
        log.info("Validation result: valid={}, errors={}", valid, errors);

        return new OrderValidationResponse(valid, errors);
    }
}
