package com.dornach.order.client;

import java.util.UUID;

/**
 * Client interface for communicating with the shipment-service.
 */
public interface ShipmentClient {

    ShipmentResponse createShipment(ShipmentRequest request);

    ShipmentResponse getShipment(UUID shipmentId);

    ShipmentResponse getShipmentByTrackingNumber(String trackingNumber);
}
