package com.dornach.lambda;

import java.util.List;

public record OrderValidationResponse(
    boolean valid,
    List<String> errors
) {}
