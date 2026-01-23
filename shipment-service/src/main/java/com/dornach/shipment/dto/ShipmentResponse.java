package com.dornach.shipment.dto;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.domain.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        String trackingNumber,
        UUID orderId,
        String recipientName,
        String recipientAddress,
        ShipmentStatus status,
        Instant createdAt,
        Instant shippedAt,
        Instant deliveredAt
) {
    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                shipment.getOrderId(),
                shipment.getRecipientName(),
                shipment.getRecipientAddress(),
                shipment.getStatus(),
                shipment.getCreatedAt(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt()
        );
    }
}
