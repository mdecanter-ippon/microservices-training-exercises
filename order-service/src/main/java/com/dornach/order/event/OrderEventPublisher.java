package com.dornach.order.event;

import com.dornach.order.domain.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    private final String queueName;

    public OrderEventPublisher(
            SqsTemplate sqsTemplate,
            ObjectMapper objectMapper,
            @Value("${app.sqs.order-events-queue:order-events}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }

    public void publishOrderCreated(Order order) {
        log.info("Publishing order created event for order: {}", order.getId());

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getProductName(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getShippingAddress(),
                order.getTrackingNumber(),
                order.getCreatedAt()
        );

        try {
            // Send as JSON string to avoid type header issues
            String jsonPayload = objectMapper.writeValueAsString(event);
            log.debug("Sending JSON payload: {}", jsonPayload);
            sqsTemplate.send(queueName, jsonPayload);
            log.info("Order event published successfully to queue: {}", queueName);
        } catch (Exception e) {
            log.error("Failed to publish order event for order: {}", order.getId(), e);
            // Don't rethrow - we don't want to fail the order creation if event publishing fails
            // In production, you might want to implement a retry mechanism or use outbox pattern
        }
    }
}
