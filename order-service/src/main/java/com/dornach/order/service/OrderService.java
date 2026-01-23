package com.dornach.order.service;

import com.dornach.order.client.*;
import com.dornach.order.domain.Order;
import com.dornach.order.dto.CreateOrderRequest;
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

    public OrderService(OrderRepository orderRepository, ShipmentClient shipmentClient, UserClient userClient) {
        this.orderRepository = orderRepository;
        this.shipmentClient = shipmentClient;
        this.userClient = userClient;
    }

    /**
     * Creates a new order with distributed tracing across services.
     * This demonstrates:
     * 1. Validating user existence via user-service
     * 2. Creating shipment via shipment-service with M2M authentication
     * 3. Automatic trace propagation across all services
     */
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.userId());

        // Step 1: Validate user exists (call to user-service)
        log.info("Validating user exists...");
        UserResponse user = userClient.getUserById(request.userId());
        log.info("User validated: {} {}", user.firstName(), user.lastName());

        // Step 2: Create the order
        Order order = new Order(
                request.userId(),
                request.productName(),
                request.quantity(),
                request.totalPrice(),
                request.shippingAddress()
        );

        Order saved = orderRepository.save(order);
        log.info("Order created with id: {}", saved.getId());

        // Step 3: Automatically create shipment (call to shipment-service with M2M)
        log.info("Creating shipment for order: {}", saved.getId());
        ShipmentRequest shipmentRequest = new ShipmentRequest(
                saved.getId(),
                user.firstName() + " " + user.lastName(),
                request.shippingAddress()
        );

        ShipmentResponse shipmentResponse = shipmentClient.createShipment(shipmentRequest);
        log.info("Shipment created with tracking number: {}", shipmentResponse.trackingNumber());

        // Step 4: Update order with shipment details
        saved.markAsShipped(shipmentResponse.id(), shipmentResponse.trackingNumber());
        Order updated = orderRepository.save(saved);

        log.info("Order {} fully processed with shipment {}", updated.getId(), shipmentResponse.trackingNumber());

        return updated;
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
     * This demonstrates synchronous inter-service communication with RestClient.
     */
    public Order confirmAndShipOrder(UUID orderId, String recipientName) {
        log.info("Confirming and shipping order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markAsConfirmed();

        // Call shipment-service to create a shipment
        ShipmentRequest shipmentRequest = new ShipmentRequest(
                order.getId(),
                recipientName,
                order.getShippingAddress()
        );

        ShipmentResponse shipmentResponse = shipmentClient.createShipment(shipmentRequest);

        order.markAsShipped(shipmentResponse.id(), shipmentResponse.trackingNumber());

        log.info("Order {} shipped with tracking number: {}", orderId, shipmentResponse.trackingNumber());

        return orderRepository.save(order);
    }

    public void cancelOrder(UUID orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.markAsCancelled();
        orderRepository.save(order);
    }
}
