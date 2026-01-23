package com.dornach.order.service;

import com.dornach.order.client.ShipmentClient;
import com.dornach.order.client.ShipmentRequest;
import com.dornach.order.client.ShipmentResponse;
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

    public OrderService(OrderRepository orderRepository, ShipmentClient shipmentClient) {
        this.orderRepository = orderRepository;
        this.shipmentClient = shipmentClient;
    }

    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.userId());

        Order order = new Order(
                request.userId(),
                request.productName(),
                request.quantity(),
                request.totalPrice(),
                request.shippingAddress()
        );

        Order saved = orderRepository.save(order);
        log.info("Order created with id: {}", saved.getId());

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
