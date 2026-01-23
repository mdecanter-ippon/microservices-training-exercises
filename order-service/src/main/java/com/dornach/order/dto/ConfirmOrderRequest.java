package com.dornach.order.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmOrderRequest(
        @NotBlank(message = "Recipient name is required")
        String recipientName
) {}
