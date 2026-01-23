package com.dornach.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Configuration for the RestClient used to communicate with other services.
 * This demonstrates the new Spring 6 RestClient which replaces RestTemplate.
 */
@Configuration
public class RestClientConfig {

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    @Bean
    public RestClient shipmentRestClient() {
        return RestClient.builder()
                .baseUrl(shipmentServiceUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
