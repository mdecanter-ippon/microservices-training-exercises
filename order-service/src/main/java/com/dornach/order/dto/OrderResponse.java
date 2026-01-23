package com.dornach.order.dto;

import com.dornach.order.domain.Order;
import com.dornach.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        String productName,
        Integer quantity,
        BigDecimal totalPrice,
        String shippingAddress,
        OrderStatus status,
        UUID shipmentId,
        String trackingNumber,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductName(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getShippingAddress(),
                order.getStatus(),
                order.getShipmentId(),
                order.getTrackingNumber(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
