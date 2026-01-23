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
 */
@Configuration
public class RestClientConfig {

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
     * RestClient configured with custom interceptor for M2M calls to shipment-service.
     * The interceptor automatically adds the Bearer token obtained via client_credentials.
     */
    @Bean
    public RestClient shipmentRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        return RestClient.builder()
                .baseUrl(shipmentServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    // Create an OAuth2AuthorizeRequest for client_credentials
                    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                            .withClientRegistrationId("shipment-service")
                            .principal("order-service")  // Principal name for M2M
                            .build();

                    // Get the authorized client (will obtain token if needed)
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
