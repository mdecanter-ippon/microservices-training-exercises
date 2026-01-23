package com.dornach.order.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "User ID is required")
        UUID userId,

        @NotBlank(message = "Product name is required")
        String productName,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "Total price is required")
        @DecimalMin(value = "0.01", message = "Total price must be greater than 0")
        BigDecimal totalPrice,

        @NotBlank(message = "Shipping address is required")
        String shippingAddress
) {}
