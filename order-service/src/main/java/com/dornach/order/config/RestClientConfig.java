package com.dornach.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    @Value("${user.service.url}")
    private String userServiceUrl;

    /**
     * OAuth2AuthorizedClientManager responsible for obtaining and managing OAuth2 tokens.
     * Uses AuthorizedClientServiceOAuth2AuthorizedClientManager for M2M authentication
     * (client_credentials flow) which works without a servlet request context.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientService);

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
            log.debug("Attempting to get M2M token for client: {}", clientRegistrationId);
            try {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId(clientRegistrationId)
                        .principal("order-service")
                        .build();

                OAuth2AuthorizedClient authorizedClient =
                        authorizedClientManager.authorize(authorizeRequest);

                if (authorizedClient != null) {
                    String token = authorizedClient.getAccessToken().getTokenValue();
                    request.getHeaders().setBearerAuth(token);
                    log.debug("M2M token added to request");
                } else {
                    log.warn("Failed to get M2M token - authorizedClient is null");
                }
            } catch (Exception e) {
                log.error("Error getting M2M token: {}", e.getMessage(), e);
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
