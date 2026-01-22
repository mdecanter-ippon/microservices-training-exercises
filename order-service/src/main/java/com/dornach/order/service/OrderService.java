package com.dornach.order.service;

import com.dornach.order.client.UserClient;
import com.dornach.order.domain.Order;
import com.dornach.order.domain.OrderStatus;
import com.dornach.order.dto.CreateOrderRequest;
import com.dornach.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class OrderService {

    private static final Logger log = Logger.getLogger(OrderService.class.getName());

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    // TODO (Step 2 - Challenge): Add ShipmentClient

    public OrderService(OrderRepository orderRepository, UserClient userClient) {
        this.orderRepository = orderRepository;
        this.userClient = userClient;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    /**
     * Create a new order.
     *
     * TODO (Step 2 - Exercise 5):
     * 1. Call userClient.getUserById() to validate the user exists
     * 2. Log the user's name
     * 3. Create and save the order with PENDING status
     * 4. Return the saved order
     */
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: " + request.userId());

        // TODO (Step 2): Validate user exists
        // var user = userClient.getUserById(request.userId());
        // log.info("User validated: " + user.firstName() + " " + user.lastName());

        Order order = new Order(
                request.userId(),
                request.productName(),
                request.quantity(),
                request.totalPrice(),
                request.shippingAddress()
        );

        return orderRepository.save(order);
    }

    /**
     * Confirm an order and create a shipment.
     *
     * TODO (Step 2 - Challenge): Implement shipment creation
     */
    public Order confirmAndShipOrder(UUID orderId) {
        Order order = getOrderById(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not in PENDING status");
        }

        // TODO: Create shipment via ShipmentClient
        // var shipment = shipmentClient.createShipment(...);
        // order.setTrackingNumber(shipment.trackingNumber());

        order.setStatus(OrderStatus.SHIPPED);
        return orderRepository.save(order);
    }
}
