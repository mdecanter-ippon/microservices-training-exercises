# Step 2: Service Communication

---

## Recap: Step 1

In Step 1, you built **user-service** with:
- **JPA Entity** `User` with annotations and timestamps
- **DTOs** using Java Records (`CreateUserRequest`, `UpdateUserRequest`, `UserResponse`)
- **Bean Validation** (`@NotBlank`, `@Email`, `@Size`)
- **REST Controller** with CRUD endpoints and proper HTTP status codes
- **Error handling** with RFC 7807 via `GlobalExceptionHandler`
- **Virtual Threads** enabled for better scalability

---

## Objectives

By the end of this exercise, you will:
- Use Spring 6's `RestClient` to call other services
- Create interface-based clients for better testability
- Configure retry and timeout with Resilience4j
- Orchestrate calls between multiple services

---

## Prerequisites

- Step 1 completed (user-service working)
- All three services available: user-service (8081), shipment-service (8082), order-service (8083)
- Basic understanding of HTTP clients and dependency injection

---

## Context

The **order-service** needs to communicate with other services:
- **user-service** → Validate that the user exists before creating an order
- **shipment-service** → Create a shipment when confirming an order

```
                                          ┌──────────────┐
                                    ┌───▶ │ user-service │
                                    │     └──────────────┘
┌─────────┐      ┌───────────────┐  │
│ Client  │ ───▶ │ order-service │ ─┤
└─────────┘      └───────────────┘  │
                                    │     ┌─────────────────┐
                                    └───▶ │ shipment-service│
                                          └─────────────────┘
```

**Current problems:**
1. Anyone can create an order with any `userId` - we need to validate the user exists
2. The `/confirm` endpoint doesn't actually create a shipment - we need to call shipment-service

---

## Exercise 1: Configure RestClient

We need `RestClient` beans to call user-service and shipment-service.

### 1.1 Create the Configuration Class

Create: `order-service/src/main/java/com/dornach/order/config/RestClientConfig.java`

You need to:
1. Create a `@Configuration` class
2. Inject URLs for both services using `@Value`
3. Create two `@Bean` methods: `userRestClient` and `shipmentRestClient`

<details>
<summary>💡 Hint: Class structure</summary>

```java
@Configuration
public class RestClientConfig {

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    @Bean
    public RestClient userRestClient(RestClient.Builder builder) {
        // TODO
    }

    @Bean
    public RestClient shipmentRestClient(RestClient.Builder builder) {
        // TODO
    }
}
```

</details>

<details>
<summary>✅ Solution</summary>

```java
package com.dornach.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    @Bean
    public RestClient userRestClient(RestClient.Builder builder) {
        return builder
            .baseUrl(userServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public RestClient shipmentRestClient(RestClient.Builder builder) {
        return builder
            .baseUrl(shipmentServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

**Why use the injected `RestClient.Builder`?** Spring Boot auto-configures it with observability (tracing, metrics). Using `RestClient.builder()` directly would bypass this.

</details>

### 1.2 Add Configuration Properties

Add to `order-service/src/main/resources/application.yaml`:

```yaml
user:
  service:
    url: http://localhost:8081

shipment:
  service:
    url: http://localhost:8082
```

> ⚠️ **Important:** Add these properties **before** the `---` separator (docker profile section). Properties after `---` only apply when that profile is active. Your local development uses the default profile.

**Verify:** Start order-service. If it starts without errors, the configuration is correct.

---

## Exercise 2: Create UserClient

We'll use an interface + implementation pattern for better testability.

### 2.1 Create the Interface

Create: `order-service/src/main/java/com/dornach/order/client/UserClient.java`

Define an interface with a method that:
- Takes a `UUID userId` parameter
- Returns a `UserResponse` (already exists in `dto/UserResponse.java`)

**Question:** Why use an interface instead of calling RestClient directly in OrderService?

<details>
<summary>💡 Answer</summary>

1. **Testability** - Easy to mock with `@MockitoBean` in tests
2. **Decoupling** - OrderService doesn't know about HTTP, RestClient, or network details
3. **Flexibility** - Can swap HTTP for gRPC, message queues, or a fake implementation

</details>

<details>
<summary>✅ Solution</summary>

```java
package com.dornach.order.client;

import com.dornach.order.dto.UserResponse;
import java.util.UUID;

public interface UserClient {
    UserResponse getUserById(UUID userId);
}
```

</details>

### 2.2 Implement the Client

Create: `order-service/src/main/java/com/dornach/order/client/UserClientImpl.java`

Implement the interface:
1. Make it a Spring `@Component`
2. Inject `userRestClient` (use `@Qualifier`)
3. Implement `getUserById()` using RestClient's fluent API
4. Handle 404 errors by throwing a `RuntimeException`

<details>
<summary>💡 Hint: RestClient GET request</summary>

```java
restClient.get()
    .uri("/users/{id}", userId)
    .retrieve()
    .onStatus(status -> status.value() == 404, (req, res) -> {
        throw new RuntimeException("User not found: " + userId);
    })
    .body(UserResponse.class);
```

</details>

<details>
<summary>✅ Solution</summary>

```java
package com.dornach.order.client;

import com.dornach.order.dto.UserResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.logging.Logger;

@Component
public class UserClientImpl implements UserClient {

    private static final Logger log = Logger.getLogger(UserClientImpl.class.getName());
    private final RestClient restClient;

    public UserClientImpl(@Qualifier("userRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user: " + userId);

        return restClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .onStatus(status -> status.value() == 404, (request, response) -> {
                throw new RuntimeException("User not found: " + userId);
            })
            .body(UserResponse.class);
    }
}
```

</details>

---

## Exercise 3: Integrate UserClient into OrderService

### 3.1 Inject UserClient

Modify `OrderService.java`:
1. Add `UserClient` as a field
2. Add it to the constructor

### 3.2 Validate User in createOrder

Update `createOrder()` to:
1. Call `userClient.getUserById()` before creating the order
2. Log the user's name to verify it worked

<details>
<summary>✅ Solution</summary>

```java
private final OrderRepository orderRepository;
private final UserClient userClient;

public OrderService(OrderRepository orderRepository, UserClient userClient) {
    this.orderRepository = orderRepository;
    this.userClient = userClient;
}

public Order createOrder(CreateOrderRequest request) {
    log.info("Creating order for user: " + request.userId());

    // Validate user exists
    UserResponse user = userClient.getUserById(request.userId());
    log.info("User validated: " + user.firstName() + " " + user.lastName());

    Order order = new Order(
            request.userId(),
            request.productName(),
            request.quantity(),
            request.totalPrice(),
            request.shippingAddress()
    );

    return orderRepository.save(order);
}
```

</details>

### 3.3 Test It

Start user-service and order-service, then:

```bash
# Create a user first
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User","role":"EMPLOYEE"}'

# Create an order with the returned user ID
curl -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":"PASTE_USER_ID","productName":"Laptop","quantity":1,"totalPrice":999.99,"shippingAddress":"123 Main St"}'
```

✅ Check logs: `User validated: Test User`

---

## Exercise 4: Create ShipmentClient

Apply the same pattern to call shipment-service.

### 4.1 Create DTOs

Create the request/response DTOs in `order-service/src/main/java/com/dornach/order/client/`:

**ShipmentRequest.java:**
```java
package com.dornach.order.client;

import java.util.UUID;

public record ShipmentRequest(
    UUID orderId,
    String recipientName,
    String shippingAddress
) {}
```

**ShipmentResponse.java:**
```java
package com.dornach.order.client;

import java.util.UUID;

public record ShipmentResponse(
    UUID id,
    String trackingNumber,
    UUID orderId,
    String status
) {}
```

### 4.2 Create the Interface

Create: `order-service/src/main/java/com/dornach/order/client/ShipmentClient.java`

```java
public interface ShipmentClient {
    ShipmentResponse createShipment(ShipmentRequest request);
}
```

### 4.3 Implement the Client

Create: `order-service/src/main/java/com/dornach/order/client/ShipmentClientImpl.java`

Similar to UserClient, but:
- Use `shipmentRestClient` (different `@Qualifier`)
- Make a POST request to `/shipments`
- Send the request body

<details>
<summary>💡 Hint: RestClient POST request</summary>

```java
restClient.post()
    .uri("/shipments")
    .body(request)
    .retrieve()
    .body(ShipmentResponse.class);
```

</details>

<details>
<summary>✅ Solution</summary>

```java
package com.dornach.order.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.logging.Logger;

@Component
public class ShipmentClientImpl implements ShipmentClient {

    private static final Logger log = Logger.getLogger(ShipmentClientImpl.class.getName());
    private final RestClient restClient;

    public ShipmentClientImpl(@Qualifier("shipmentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ShipmentResponse createShipment(ShipmentRequest request) {
        log.info("Creating shipment for order: " + request.orderId());

        return restClient.post()
            .uri("/shipments")
            .body(request)
            .retrieve()
            .body(ShipmentResponse.class);
    }
}
```

</details>

---

## Exercise 5: Integrate ShipmentClient into OrderService

### 5.1 Create ConfirmOrderRequest DTO

Create: `order-service/src/main/java/com/dornach/order/dto/ConfirmOrderRequest.java`

```java
package com.dornach.order.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmOrderRequest(
    @NotBlank(message = "Recipient name is required")
    String recipientName
) {}
```

### 5.2 Update OrderService

1. Inject `ShipmentClient`
2. Replace `confirmOrder()` with `confirmAndShipOrder(UUID orderId, String recipientName)`:
   - Find the order
   - Mark as CONFIRMED
   - Call shipment-service
   - Update order with tracking number
   - Mark as SHIPPED

<details>
<summary>✅ Solution</summary>

```java
private final OrderRepository orderRepository;
private final UserClient userClient;
private final ShipmentClient shipmentClient;

public OrderService(OrderRepository orderRepository, UserClient userClient, ShipmentClient shipmentClient) {
    this.orderRepository = orderRepository;
    this.userClient = userClient;
    this.shipmentClient = shipmentClient;
}

public Order confirmAndShipOrder(UUID orderId, String recipientName) {
    log.info("Confirming and shipping order: " + orderId);

    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

    if (order.getStatus() != OrderStatus.PENDING) {
        throw new IllegalStateException("Order is not in PENDING status");
    }

    order.setStatus(OrderStatus.CONFIRMED);

    // Call shipment-service
    ShipmentRequest shipmentRequest = new ShipmentRequest(
            order.getId(),
            recipientName,
            order.getShippingAddress()
    );
    ShipmentResponse shipment = shipmentClient.createShipment(shipmentRequest);
    log.info("Shipment created with tracking: " + shipment.trackingNumber());

    order.setTrackingNumber(shipment.trackingNumber());
    order.setStatus(OrderStatus.SHIPPED);

    return orderRepository.save(order);
}
```

</details>

### 5.3 Update OrderController

Update the `/confirm` endpoint to accept a body and call the new method:

```java
@PostMapping("/{id}/confirm")
public ResponseEntity<OrderResponse> confirmOrder(
        @PathVariable UUID id,
        @Valid @RequestBody ConfirmOrderRequest request) {
    var order = orderService.confirmAndShipOrder(id, request.recipientName());
    return ResponseEntity.ok(OrderResponse.from(order));
}
```

### 5.4 Test It

Start all three services, then:

```bash
# Create a user
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User","role":"EMPLOYEE"}'

# Create an order
curl -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":"USER_ID","productName":"Laptop","quantity":1,"totalPrice":999.99,"shippingAddress":"123 Main St"}'

# Confirm the order (creates shipment)
curl -X POST http://localhost:8083/orders/ORDER_ID/confirm \
  -H "Content-Type: application/json" \
  -d '{"recipientName":"Test User"}'
```

✅ Check logs: `Shipment created with tracking: TRACK-XXXX`

---

## Exercise 6: Add Resilience

Network calls can fail. Add retry and timeout using Resilience4j.

### 6.1 Configure Resilience4j

Add to `application.yaml`:

```yaml
resilience4j:
  retry:
    instances:
      userService:
        maxAttempts: 3
        waitDuration: 500ms
      shipmentService:
        maxAttempts: 3
        waitDuration: 500ms
  timelimiter:
    instances:
      userService:
        timeoutDuration: 2s
      shipmentService:
        timeoutDuration: 2s
```

### 6.2 Apply to Clients

Add `@Retry` annotation to both client implementations:

```java
@Override
@Retry(name = "userService", fallbackMethod = "getUserByIdFallback")
public UserResponse getUserById(UUID userId) {
    // existing code
}

private UserResponse getUserByIdFallback(UUID userId, Exception ex) {
    log.severe("User service unavailable: " + ex.getMessage());
    throw new RuntimeException("User service is unavailable");
}
```

Do the same for `ShipmentClientImpl` with `name = "shipmentService"`.

### 6.3 Test Retry

1. Stop user-service
2. Try to create an order
3. Watch order-service logs - you'll see retry attempts

---

## Exercise 7: Integration Test

Create: `order-service/src/test/java/com/dornach/order/controller/OrderControllerIntegrationTest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private ShipmentClient shipmentClient;

    @Test
    void createOrder_ValidUser_ReturnsCreated() throws Exception {
        when(userClient.getUserById(any(UUID.class)))
            .thenReturn(new UserResponse(
                UUID.randomUUID(), "test@example.com", "Test", "User",
                "EMPLOYEE", "ACTIVE", null, null
            ));

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "11111111-1111-1111-1111-111111111111",
                      "productName": "Laptop",
                      "quantity": 1,
                      "totalPrice": 999.99,
                      "shippingAddress": "123 Main St"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
```

---

## Validation Checklist

Before moving to Step 3, verify:

- [ ] `RestClientConfig` creates both `userRestClient` and `shipmentRestClient`
- [ ] `UserClient` validates users on order creation
- [ ] `ShipmentClient` creates shipments on order confirmation
- [ ] `POST /orders` validates user exists first
- [ ] `POST /orders/{id}/confirm` creates a shipment and returns tracking number
- [ ] Retry works: stop a service, see retry attempts in logs
- [ ] Tests pass: `mvn test`

---

## Summary

In this step, you learned:
- **RestClient** (Spring 6) provides a modern, fluent API for HTTP calls
- **Interface-based clients** improve testability and decoupling
- **Resilience4j** adds retry and timeout to handle network failures
- **Service orchestration** coordinates calls between multiple services

---

## Before Moving On

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 2"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-2-complete
```

**Next:** [Step 3 - Contract-First API](./STEP_3_CONTRACT_FIRST.md)
