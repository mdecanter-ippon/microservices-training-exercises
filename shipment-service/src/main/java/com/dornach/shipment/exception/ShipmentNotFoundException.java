package com.dornach.shipment.exception;

import java.util.UUID;

public class ShipmentNotFoundException extends RuntimeException {

    private final UUID shipmentId;

    public ShipmentNotFoundException(UUID shipmentId) {
        super("Shipment not found with id: " + shipmentId);
        this.shipmentId = shipmentId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }
}
