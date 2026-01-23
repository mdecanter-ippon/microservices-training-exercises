package com.dornach.shipment.controller;

import com.dornach.shipment.domain.Shipment;
import com.dornach.shipment.domain.ShipmentStatus;
import com.dornach.shipment.dto.CreateShipmentRequest;
import com.dornach.shipment.dto.UpdateShipmentStatusRequest;
import com.dornach.shipment.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.dornach.shipment.config.TestSecurityConfig.class)
@WithMockUser(roles = {"service-caller", "admin"})
class ShipmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /shipments - should create shipment and return 201")
    void createShipment_Success() throws Exception {
        var orderId = UUID.randomUUID();
        var request = new CreateShipmentRequest(orderId, "Alice Martin", "123 Main St, Paris");

        mockMvc.perform(post("/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.trackingNumber").exists())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.recipientName").value("Alice Martin"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /shipments/{id} - should return shipment")
    void getShipmentById_Success() throws Exception {
        var shipment = shipmentRepository.save(
                new Shipment(UUID.randomUUID(), "Bob Dupont", "456 Oak Ave, Lyon")
        );

        mockMvc.perform(get("/shipments/{id}", shipment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipment.getId().toString()))
                .andExpect(jsonPath("$.recipientName").value("Bob Dupont"));
    }

    @Test
    @DisplayName("GET /shipments/{id} - should return 404 for unknown shipment")
    void getShipmentById_NotFound() throws Exception {
        mockMvc.perform(get("/shipments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Shipment Not Found"));
    }

    @Test
    @DisplayName("GET /shipments/tracking/{trackingNumber} - should return shipment")
    void getShipmentByTrackingNumber_Success() throws Exception {
        var shipment = shipmentRepository.save(
                new Shipment(UUID.randomUUID(), "Charlie Brown", "789 Pine Rd, Marseille")
        );

        mockMvc.perform(get("/shipments/tracking/{trackingNumber}", shipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(shipment.getTrackingNumber()));
    }

    @Test
    @DisplayName("PATCH /shipments/{id}/status - should update status to SHIPPED")
    void updateShipmentStatus_ToShipped() throws Exception {
        var shipment = shipmentRepository.save(
                new Shipment(UUID.randomUUID(), "David Wilson", "321 Elm St, Bordeaux")
        );

        var request = new UpdateShipmentStatusRequest(ShipmentStatus.SHIPPED);

        mockMvc.perform(patch("/shipments/{id}/status", shipment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shippedAt").exists());
    }

    @Test
    @DisplayName("PATCH /shipments/{id}/status - should return 422 for invalid transition")
    void updateShipmentStatus_InvalidTransition() throws Exception {
        var shipment = shipmentRepository.save(
                new Shipment(UUID.randomUUID(), "Eve Johnson", "654 Maple Dr, Nice")
        );
        shipment.markAsDelivered();
        shipmentRepository.save(shipment);

        var request = new UpdateShipmentStatusRequest(ShipmentStatus.SHIPPED);

        mockMvc.perform(patch("/shipments/{id}/status", shipment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Invalid Status Transition"));
    }

    @Test
    @DisplayName("GET /shipments/order/{orderId} - should return shipments for order")
    void getShipmentsByOrderId() throws Exception {
        var orderId = UUID.randomUUID();
        shipmentRepository.save(new Shipment(orderId, "Frank Miller", "111 First St, Toulouse"));
        shipmentRepository.save(new Shipment(orderId, "Frank Miller", "111 First St, Toulouse"));

        mockMvc.perform(get("/shipments/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
