package com.dornach.order.client;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a shipment response from the shipment-service API.
 */
public record ShipmentResponse(
        UUID id,
        String trackingNumber,
        UUID orderId,
        String recipientName,
        String recipientAddress,
        String status,
        Instant createdAt,
        Instant shippedAt,
        Instant deliveredAt
) {}
