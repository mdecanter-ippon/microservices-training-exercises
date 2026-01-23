package com.dornach.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        String productName,
        Integer quantity,
        BigDecimal totalPrice,
        String shippingAddress,
        String trackingNumber,
        Instant createdAt
) {
}
