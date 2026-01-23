package com.dornach.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.dornach.order.client.ShipmentClient;

@SpringBootTest
class OrderServiceApplicationTests {

    @MockitoBean
    private ShipmentClient shipmentClient;

    @Test
    void contextLoads() {
    }
}
