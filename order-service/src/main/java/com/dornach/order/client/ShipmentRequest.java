package com.dornach.order.client;

import java.util.UUID;

/**
 * DTO for creating a shipment via the shipment-service API.
 */
public record ShipmentRequest(
        UUID orderId,
        String recipientName,
        String recipientAddress
) {}
