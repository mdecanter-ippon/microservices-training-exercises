package com.dornach.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the RestClient used to communicate with other services.
 * This demonstrates the new Spring 6 RestClient which replaces RestTemplate.
 *
 * Why use the injected RestClient.Builder? Spring Boot auto-configures it with
 * observability (tracing, metrics). Using RestClient.builder() directly would bypass this.
 */
@Configuration
public class RestClientConfig {

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    /**
     * OAuth2AuthorizedClientManager responsible for obtaining and managing OAuth2 tokens.
     * This is required for client_credentials flow (M2M authentication).
     */
    @Bean
    public RestClient userRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(userServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public RestClient shipmentRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(shipmentServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
