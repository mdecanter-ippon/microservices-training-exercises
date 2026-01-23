package com.dornach.order.client;

/**
 * Exception thrown when communication with the shipment-service fails.
 */
public class ShipmentServiceException extends RuntimeException {

    public ShipmentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
