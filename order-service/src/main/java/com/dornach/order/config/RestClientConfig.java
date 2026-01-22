package com.dornach.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Configuration for RestClient beans.
 *
 * TODO (Step 2 - Exercise 1): Configure RestClient for calling other services
 */
@Configuration
public class RestClientConfig {

    @Value("${user.service.url:http://localhost:8081}")
    private String userServiceUrl;

    @Value("${shipment.service.url:http://localhost:8082}")
    private String shipmentServiceUrl;

    /**
     * RestClient for calling user-service.
     *
     * TODO (Step 2 - Exercise 1):
     * 1. Use the builder (not RestClient.builder() - important for tracing!)
     * 2. Set the base URL from userServiceUrl
     * 3. Add default Content-Type header (application/json)
     * 4. Return the built RestClient
     */
    @Bean
    public RestClient userRestClient(RestClient.Builder builder) {
        // TODO: Implement
        // Hint:
        // return builder
        //     .baseUrl(userServiceUrl)
        //     .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        //     .build();

        return builder.baseUrl(userServiceUrl).build();
    }

    /**
     * RestClient for calling shipment-service.
     *
     * TODO (Step 2): Implement similarly to userRestClient
     */
    @Bean
    public RestClient shipmentRestClient(RestClient.Builder builder) {
        return builder.baseUrl(shipmentServiceUrl).build();
    }
}
