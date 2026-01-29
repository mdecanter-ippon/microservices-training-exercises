package com.dornach.order.controller;

import com.dornach.order.client.ShipmentClient;
import com.dornach.order.client.UserClient;
import com.dornach.order.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for OrderController.
 * Uses @MockitoBean to mock external service clients (UserClient, ShipmentClient)
 * so we can test order-service in isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private ShipmentClient shipmentClient;

    // TODO: Implement this test
    // @Test
    // void createOrder_ValidUser_ReturnsCreated() throws Exception {
    //     // 1. Mock userClient.getUserById() to return a valid user
    //     // 2. Perform POST /orders with valid request body
    //     // 3. Assert status is 201 Created
    //     // 4. Assert response contains status "PENDING"
    // }
}
