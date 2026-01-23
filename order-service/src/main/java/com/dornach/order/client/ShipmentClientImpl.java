package com.dornach.order.client;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * REST client implementation for communicating with the shipment-service.
 * Uses the new Spring 6 RestClient with Resilience4j for fault tolerance.
 */
@Component
public class ShipmentClientImpl implements ShipmentClient {

    private static final Logger log = LoggerFactory.getLogger(ShipmentClientImpl.class);

    private final RestClient restClient;

    public ShipmentClientImpl(RestClient shipmentRestClient) {
        this.restClient = shipmentRestClient;
    }

    @Override
    @Retry(name = "shipmentService", fallbackMethod = "createShipmentFallback")
    public ShipmentResponse createShipment(ShipmentRequest request) {
        log.info("Creating shipment for order: {}", request.orderId());

        return restClient.post()
                .uri("/shipments")
                .body(request)
                .retrieve()
                .body(ShipmentResponse.class);
    }

    @Override
    @Retry(name = "shipmentService", fallbackMethod = "getShipmentFallback")
    public ShipmentResponse getShipment(UUID shipmentId) {
        log.debug("Fetching shipment: {}", shipmentId);

        return restClient.get()
                .uri("/shipments/{id}", shipmentId)
                .retrieve()
                .body(ShipmentResponse.class);
    }

    @Override
    @Retry(name = "shipmentService", fallbackMethod = "getShipmentByTrackingFallback")
    public ShipmentResponse getShipmentByTrackingNumber(String trackingNumber) {
        log.debug("Fetching shipment by tracking number: {}", trackingNumber);

        return restClient.get()
                .uri("/shipments/tracking/{trackingNumber}", trackingNumber)
                .retrieve()
                .body(ShipmentResponse.class);
    }

    @SuppressWarnings("unused")
    private ShipmentResponse createShipmentFallback(ShipmentRequest request, Exception ex) {
        log.error("Failed to create shipment for order {} after retries: {}", request.orderId(), ex.getMessage());
        throw new ShipmentServiceException("Shipment service is unavailable", ex);
    }

    @SuppressWarnings("unused")
    private ShipmentResponse getShipmentFallback(UUID shipmentId, Exception ex) {
        log.error("Failed to fetch shipment {} after retries: {}", shipmentId, ex.getMessage());
        throw new ShipmentServiceException("Shipment service is unavailable", ex);
    }

    @SuppressWarnings("unused")
    private ShipmentResponse getShipmentByTrackingFallback(String trackingNumber, Exception ex) {
        log.error("Failed to fetch shipment by tracking {} after retries: {}", trackingNumber, ex.getMessage());
        throw new ShipmentServiceException("Shipment service is unavailable", ex);
    }
}
