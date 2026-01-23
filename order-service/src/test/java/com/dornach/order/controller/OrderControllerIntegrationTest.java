package com.dornach.order.controller;

import com.dornach.order.client.ShipmentClient;
import com.dornach.order.client.ShipmentRequest;
import com.dornach.order.client.ShipmentResponse;
import com.dornach.order.client.UserClient;
import com.dornach.order.client.UserResponse;
import com.dornach.order.domain.Order;
import com.dornach.order.event.OrderEventPublisher;
import com.dornach.order.domain.OrderStatus;
import com.dornach.order.dto.ConfirmOrderRequest;
import com.dornach.order.dto.CreateOrderRequest;
import com.dornach.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ShipmentClient shipmentClient;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private OrderEventPublisher orderEventPublisher;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // Default mock: user exists
        when(userClient.getUserById(any(UUID.class)))
                .thenReturn(new UserResponse(
                        UUID.randomUUID(),
                        "test@example.com",
                        "Test",
                        "User",
                        "user",
                        "ACTIVE",
                        Instant.now(),
                        Instant.now()
                ));
    }

    @Test
    @DisplayName("POST /orders - should create order and return 201")
    void createOrder_Success() throws Exception {
        var request = new CreateOrderRequest(
                UUID.randomUUID(),
                "Laptop Pro 15",
                2,
                new BigDecimal("2499.99"),
                "123 Main St, Paris"
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.productName").value("Laptop Pro 15"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /orders - should return 400 for invalid request")
    void createOrder_InvalidRequest() throws Exception {
        var request = new CreateOrderRequest(
                null,
                "",
                0,
                new BigDecimal("-1"),
                ""
        );

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.properties.fieldErrors").exists());
    }

    @Test
    @DisplayName("GET /orders/{id} - should return order")
    void getOrderById_Success() throws Exception {
        var order = orderRepository.save(new Order(
                UUID.randomUUID(),
                "Keyboard Mechanical",
                1,
                new BigDecimal("149.99"),
                "456 Oak Ave, Lyon"
        ));

        mockMvc.perform(get("/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.productName").value("Keyboard Mechanical"));
    }

    @Test
    @DisplayName("GET /orders/{id} - should return 404 for unknown order")
    void getOrderById_NotFound() throws Exception {
        mockMvc.perform(get("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"));
    }

    @Test
    @DisplayName("POST /orders/{id}/confirm - should confirm order and create shipment")
    void confirmAndShipOrder_Success() throws Exception {
        var order = orderRepository.save(new Order(
                UUID.randomUUID(),
                "Monitor 27 inch",
                1,
                new BigDecimal("599.99"),
                "789 Pine Rd, Marseille"
        ));

        var shipmentId = UUID.randomUUID();
        var trackingNumber = "SHIP-123456";

        when(shipmentClient.createShipment(any(ShipmentRequest.class)))
                .thenReturn(new ShipmentResponse(
                        shipmentId,
                        trackingNumber,
                        order.getId(),
                        "Alice Martin",
                        order.getShippingAddress(),
                        "PENDING",
                        Instant.now(),
                        null,
                        null
                ));

        var request = new ConfirmOrderRequest("Alice Martin");

        mockMvc.perform(post("/orders/{id}/confirm", order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipmentId").value(shipmentId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel - should cancel order")
    void cancelOrder_Success() throws Exception {
        var order = orderRepository.save(new Order(
                UUID.randomUUID(),
                "Mouse Wireless",
                3,
                new BigDecimal("89.97"),
                "321 Elm St, Bordeaux"
        ));

        mockMvc.perform(post("/orders/{id}/cancel", order.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/orders/{id}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("GET /orders/user/{userId} - should return orders for user")
    void getOrdersByUserId() throws Exception {
        var userId = UUID.randomUUID();
        orderRepository.save(new Order(userId, "Product A", 1, new BigDecimal("10"), "Address 1"));
        orderRepository.save(new Order(userId, "Product B", 2, new BigDecimal("20"), "Address 2"));

        mockMvc.perform(get("/orders/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
