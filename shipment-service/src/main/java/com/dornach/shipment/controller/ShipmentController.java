package com.dornach.shipment.controller;

import com.dornach.shipment.dto.CreateShipmentRequest;
import com.dornach.shipment.dto.ShipmentResponse;
import com.dornach.shipment.dto.UpdateShipmentStatusRequest;
import com.dornach.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shipments")
@Tag(name = "Shipments", description = "Shipment management endpoints")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('service-caller') or hasRole('admin')")
    @Operation(summary = "Create a new shipment (requires service-caller or admin role)")
    @ApiResponse(responseCode = "201", description = "Shipment created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Forbidden - missing required role")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        var shipment = shipmentService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShipmentResponse.from(shipment));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get shipment by ID")
    @ApiResponse(responseCode = "200", description = "Shipment found")
    @ApiResponse(responseCode = "404", description = "Shipment not found")
    public ResponseEntity<ShipmentResponse> getShipmentById(@PathVariable UUID id) {
        var shipment = shipmentService.getShipmentById(id);
        return ResponseEntity.ok(ShipmentResponse.from(shipment));
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Get shipment by tracking number")
    @ApiResponse(responseCode = "200", description = "Shipment found")
    @ApiResponse(responseCode = "404", description = "Shipment not found")
    public ResponseEntity<ShipmentResponse> getShipmentByTrackingNumber(@PathVariable String trackingNumber) {
        var shipment = shipmentService.getShipmentByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(ShipmentResponse.from(shipment));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get shipments for an order")
    @ApiResponse(responseCode = "200", description = "List of shipments")
    public ResponseEntity<List<ShipmentResponse>> getShipmentsByOrderId(@PathVariable UUID orderId) {
        var shipments = shipmentService.getShipmentsByOrderId(orderId)
                .stream()
                .map(ShipmentResponse::from)
                .toList();
        return ResponseEntity.ok(shipments);
    }

    @GetMapping
    @Operation(summary = "Get all shipments")
    @ApiResponse(responseCode = "200", description = "List of shipments")
    public ResponseEntity<List<ShipmentResponse>> getAllShipments() {
        var shipments = shipmentService.getAllShipments()
                .stream()
                .map(ShipmentResponse::from)
                .toList();
        return ResponseEntity.ok(shipments);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update shipment status")
    @ApiResponse(responseCode = "200", description = "Status updated successfully")
    @ApiResponse(responseCode = "404", description = "Shipment not found")
    @ApiResponse(responseCode = "422", description = "Invalid status transition")
    public ResponseEntity<ShipmentResponse> updateShipmentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShipmentStatusRequest request) {
        var shipment = shipmentService.updateShipmentStatus(id, request.status());
        return ResponseEntity.ok(ShipmentResponse.from(shipment));
    }
}
