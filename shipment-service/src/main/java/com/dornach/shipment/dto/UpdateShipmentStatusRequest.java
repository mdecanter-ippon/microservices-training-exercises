package com.dornach.shipment.dto;

import com.dornach.shipment.domain.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateShipmentStatusRequest(
        @NotNull(message = "Status is required")
        ShipmentStatus status
) {}
