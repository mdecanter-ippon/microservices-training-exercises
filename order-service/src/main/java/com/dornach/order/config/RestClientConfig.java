package com.dornach.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the RestClient used to communicate with other services.
 *
 * Key points for distributed tracing:
 * - Uses RestClient.Builder injected by Spring Boot (auto-configured with Micrometer instrumentation)
 * - This ensures trace context (traceId, spanId) is automatically propagated via HTTP headers
 * - The traceparent header (W3C Trace Context) is added to all outgoing requests
 */
@Configuration
public class RestClientConfig {

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    @Value("${user.service.url}")
    private String userServiceUrl;

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
     * Creates an OAuth2 interceptor that adds Bearer token to outgoing requests.
     * Reusable for multiple RestClient instances.
     */
    private ClientHttpRequestInterceptor createOAuth2Interceptor(
            OAuth2AuthorizedClientManager authorizedClientManager,
            String clientRegistrationId) {

        return (request, body, execution) -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientRegistrationId)
                    .principal("order-service")
                    .build();

            OAuth2AuthorizedClient authorizedClient =
                    authorizedClientManager.authorize(authorizeRequest);

            if (authorizedClient != null) {
                String token = authorizedClient.getAccessToken().getTokenValue();
                request.getHeaders().setBearerAuth(token);
            }

            return execution.execute(request, body);
        };
    }

    /**
     * RestClient for shipment-service with M2M authentication and trace propagation.
     *
     * IMPORTANT: We use the injected RestClient.Builder (not RestClient.builder())
     * because Spring Boot auto-configures it with Micrometer tracing instrumentation.
     * This ensures trace context is propagated to downstream services.
     */
    @Bean
    public RestClient shipmentRestClient(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        return builder
                .baseUrl(shipmentServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(createOAuth2Interceptor(authorizedClientManager, "shipment-service"))
                .build();
    }

    /**
     * RestClient for user-service with M2M authentication and trace propagation.
     *
     * Uses the same client registration as shipment-service since both services
     * accept the service-caller role from our M2M client.
     */
    @Bean
    public RestClient userRestClient(
            RestClient.Builder builder,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        return builder
                .clone()  // Clone to avoid sharing state with shipmentRestClient
                .baseUrl(userServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(createOAuth2Interceptor(authorizedClientManager, "shipment-service"))
                .build();
    }
}
