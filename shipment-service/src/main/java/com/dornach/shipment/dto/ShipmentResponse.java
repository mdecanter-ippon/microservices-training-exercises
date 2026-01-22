package com.dornach.shipment.dto;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.domain.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
    UUID id,
    UUID orderId,
    String trackingNumber,
    String recipientName,
    String recipientAddress,
    ShipmentStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
            shipment.getId(),
            shipment.getOrderId(),
            shipment.getTrackingNumber(),
            shipment.getRecipientName(),
            shipment.getRecipientAddress(),
            shipment.getStatus(),
            shipment.getCreatedAt(),
            shipment.getUpdatedAt()
        );
    }
}
