package com.dornach.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
    UUID userId,
    String productName,
    int quantity,
    BigDecimal totalPrice,
    String shippingAddress
) {}
