package com.dornach.shipment.dto;

import java.util.UUID;

public record CreateShipmentRequest(
    UUID orderId,
    String recipientName,
    String recipientAddress
) {}
