package com.dornach.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateShipmentRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotBlank(message = "Recipient name is required")
        String recipientName,

        @NotBlank(message = "Recipient address is required")
        String recipientAddress
) {}
