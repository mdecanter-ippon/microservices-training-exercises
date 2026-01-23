package com.dornach.order.service;

import com.dornach.order.client.*;
import com.dornach.order.domain.Order;
import com.dornach.order.dto.CreateOrderRequest;
import com.dornach.order.event.OrderEventPublisher;
import com.dornach.order.exception.OrderNotFoundException;
import com.dornach.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ShipmentClient shipmentClient;
    private final UserClient userClient;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(OrderRepository orderRepository, ShipmentClient shipmentClient, UserClient userClient,
                        OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.shipmentClient = shipmentClient;
        this.userClient = userClient;
        this.orderEventPublisher = orderEventPublisher;
    }

    /**
     * Creates a new order with user validation.
     * The order is created in PENDING status and must be confirmed separately.
     * This demonstrates:
     * 1. Validating user existence via user-service (synchronous)
     * 2. Order creation with proper validation
     */
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.userId());

        // Step 1: Validate user exists (call to user-service)
        log.info("Validating user exists...");
        UserResponse user = userClient.getUserById(request.userId());
        log.info("User validated: {} {}", user.firstName(), user.lastName());

        // Step 2: Create the order in PENDING status
        Order order = new Order(
                request.userId(),
                request.productName(),
                request.quantity(),
                request.totalPrice(),
                request.shippingAddress()
        );

        Order saved = orderRepository.save(order);
        log.info("Order created with id: {} in PENDING status", saved.getId());

        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(UUID id) {
        log.debug("Fetching order by id: {}", id);

        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUserId(UUID userId) {
        log.debug("Fetching orders for user: {}", userId);

        return orderRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        log.debug("Fetching all orders");

        return orderRepository.findAll();
    }

    /**
     * Confirms an order and creates a shipment via the shipment-service.
     * This demonstrates:
     * 1. Synchronous inter-service communication with RestClient (M2M auth)
     * 2. Asynchronous event publishing via SQS for notification-service
     */
    public Order confirmAndShipOrder(UUID orderId, String recipientName) {
        log.info("Confirming and shipping order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markAsConfirmed();

        // Step 1: Call shipment-service to create a shipment (M2M auth)
        log.info("Creating shipment for order: {}", orderId);
        ShipmentRequest shipmentRequest = new ShipmentRequest(
                order.getId(),
                recipientName,
                order.getShippingAddress()
        );

        ShipmentResponse shipmentResponse = shipmentClient.createShipment(shipmentRequest);
        log.info("Shipment created with tracking number: {}", shipmentResponse.trackingNumber());

        order.markAsShipped(shipmentResponse.id(), shipmentResponse.trackingNumber());
        Order updated = orderRepository.save(order);

        // Step 2: Publish order event to SQS for async notification
        log.info("Publishing order event to SQS...");
        orderEventPublisher.publishOrderCreated(updated);

        log.info("Order {} shipped with tracking number: {}", orderId, shipmentResponse.trackingNumber());
        return updated;
    }

    public void cancelOrder(UUID orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markAsCancelled();
        orderRepository.save(order);
    }
}
