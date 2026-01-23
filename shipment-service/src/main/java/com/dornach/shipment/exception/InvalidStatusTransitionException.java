package com.dornach.shipment.exception;

import com.dornach.shipment.domain.ShipmentStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    private final ShipmentStatus currentStatus;
    private final ShipmentStatus targetStatus;

    public InvalidStatusTransitionException(ShipmentStatus currentStatus, ShipmentStatus targetStatus) {
        super("Invalid status transition from " + currentStatus + " to " + targetStatus);
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public ShipmentStatus getCurrentStatus() {
        return currentStatus;
    }

    public ShipmentStatus getTargetStatus() {
        return targetStatus;
    }
}
