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
 * This demonstrates the new Spring 6 RestClient with OAuth2 Client Credentials flow
 * for Machine-to-Machine (M2M) authentication.
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
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientRepository);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    /**
     * RestClient for user-service with M2M authentication.
     */
    @Bean
    public RestClient userRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager) {
        return builder
                .baseUrl(userServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                            .withClientRegistrationId("shipment-service")
                            .principal("order-service")
                            .build();

                    OAuth2AuthorizedClient authorizedClient =
                            authorizedClientManager.authorize(authorizeRequest);

                    if (authorizedClient != null) {
                        String token = authorizedClient.getAccessToken().getTokenValue();
                        request.getHeaders().setBearerAuth(token);
                    }

                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * RestClient for shipment-service with M2M authentication.
     */
    @Bean
    public RestClient shipmentRestClient(RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager) {
        return builder
                .baseUrl(shipmentServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                            .withClientRegistrationId("shipment-service")
                            .principal("order-service")
                            .build();

                    OAuth2AuthorizedClient authorizedClient =
                            authorizedClientManager.authorize(authorizeRequest);

                    if (authorizedClient != null) {
                        String token = authorizedClient.getAccessToken().getTokenValue();
                        request.getHeaders().setBearerAuth(token);
                    }

                    return execution.execute(request, body);
                })
                .build();
    }
}
