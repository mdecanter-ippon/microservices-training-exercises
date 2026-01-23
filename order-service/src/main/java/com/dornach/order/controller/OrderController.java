package com.dornach.order.controller;

import com.dornach.order.dto.ConfirmOrderRequest;
import com.dornach.order.dto.CreateOrderRequest;
import com.dornach.order.dto.OrderResponse;
import com.dornach.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order management and orchestration endpoints")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create a new order")
    @ApiResponse(responseCode = "201", description = "Order created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        var order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        var order = orderService.getOrderById(id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get orders for a user")
    @ApiResponse(responseCode = "200", description = "List of orders")
    public ResponseEntity<List<OrderResponse>> getOrdersByUserId(@PathVariable UUID userId) {
        var orders = orderService.getOrdersByUserId(userId)
                .stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping
    @Operation(summary = "Get all orders")
    @ApiResponse(responseCode = "200", description = "List of orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        var orders = orderService.getAllOrders()
                .stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm order and create shipment",
            description = "Confirms the order and calls shipment-service to create a shipment")
    @ApiResponse(responseCode = "200", description = "Order confirmed and shipped")
    @ApiResponse(responseCode = "404", description = "Order not found")
    @ApiResponse(responseCode = "503", description = "Shipment service unavailable")
    public ResponseEntity<OrderResponse> confirmAndShipOrder(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmOrderRequest request) {
        var order = orderService.confirmAndShipOrder(id, request.recipientName());
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order")
    @ApiResponse(responseCode = "204", description = "Order cancelled")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<Void> cancelOrder(@PathVariable UUID id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }
}
