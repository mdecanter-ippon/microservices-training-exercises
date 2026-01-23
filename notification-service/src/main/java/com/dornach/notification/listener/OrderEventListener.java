package com.dornach.notification.listener;

import com.dornach.notification.dto.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;

    public OrderEventListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SqsListener("order-events")
    public void handleOrderCreatedEvent(String message) {
        log.debug("Received raw message from SQS: {}", message);

        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(message, OrderCreatedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize message: {}", e.getMessage());
            throw new RuntimeException("Failed to process message", e);
        }

        log.info("========================================");
        log.info("Received order event from SQS");
        log.info("========================================");
        log.info("Order ID: {}", event.orderId());
        log.info("User ID: {}", event.userId());
        log.info("Product: {} x{}", event.productName(), event.quantity());
        log.info("Total Price: {}", event.totalPrice());
        log.info("Shipping Address: {}", event.shippingAddress());
        log.info("Tracking Number: {}", event.trackingNumber());
        log.info("Created At: {}", event.createdAt());

        // Simulate calling a legacy notification system
        log.info("Simulating call to legacy notification system...");
        simulateLegacyNotificationCall(event);

        log.info("Order event processed successfully!");
        log.info("========================================");
    }

    private void simulateLegacyNotificationCall(OrderCreatedEvent event) {
        log.info("  -> Preparing notification payload for legacy system...");

        try {
            // Simulate network latency to a legacy system
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Legacy call interrupted");
        }

        log.info("  -> Legacy notification sent for order: {}", event.orderId());
        log.info("  -> Notification details:");
        log.info("     - Type: ORDER_CONFIRMATION");
        log.info("     - Recipient: User {}", event.userId());
        log.info("     - Subject: Your order {} has been placed", event.orderId());
        log.info("     - Status: DELIVERED_TO_LEGACY_SYSTEM");
    }
}
