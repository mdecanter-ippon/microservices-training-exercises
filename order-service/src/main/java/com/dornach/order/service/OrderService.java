package com.dornach.order.service;

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

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
    }

    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: " + request.userId());

        Order order = new Order(
                request.userId(),
                request.productName(),
                request.quantity(),
                request.totalPrice(),
                request.shippingAddress()
        );

        return orderRepository.save(order);
    }

    public Order confirmOrder(UUID orderId) {
        Order order = getOrderById(orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order is not in PENDING status");
        }

        order.setStatus(OrderStatus.CONFIRMED);
        return orderRepository.save(order);
    }
}
