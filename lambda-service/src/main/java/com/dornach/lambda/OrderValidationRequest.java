package com.dornach.lambda;

import java.math.BigDecimal;

public record OrderValidationRequest(
    String userId,
    String productName,
    Integer quantity,
    BigDecimal totalPrice
) {}
