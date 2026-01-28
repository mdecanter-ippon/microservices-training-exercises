# Step 2: Service Communication

> **⚠️ Before starting:** Make sure you have completed Step 1.
> If you need to catch up:
> ```bash
> git stash && git checkout step-1-complete
> ```

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
- Both services running: user-service (8081) and order-service (8083)
- Basic understanding of HTTP clients and dependency injection

---

## Context

The **order-service** needs to validate that a user exists before creating an order. This requires calling the **user-service**.

```
┌─────────────┐      ┌───────────────┐      ┌──────────────┐
│   Client    │ ──── │ order-service │ ──── │ user-service │
└─────────────┘      └───────────────┘      └──────────────┘
                      POST /orders            GET /users/{id}
```

**Current problem:** Anyone can create an order with any `userId`. We need to validate that the user actually exists in user-service.

---

## Exercise 1: Configure RestClient

We need a `RestClient` bean configured to call user-service.

### 1.1 Create the Configuration Class

Create a new file: `order-service/src/main/java/com/dornach/order/config/RestClientConfig.java`

You need to:
1. Create a `@Configuration` class
2. Inject the user-service URL from configuration using `@Value`
3. Create a `@Bean` method that returns a configured `RestClient`

**Think about:**
- How do you inject a property value in Spring?
- What's the builder pattern for RestClient?
- Why should you use the injected `RestClient.Builder` instead of `RestClient.builder()`?

<details>
<summary>💡 Hint 1: Class structure</summary>

```java
@Configuration
public class RestClientConfig {

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Bean
    public RestClient userRestClient(RestClient.Builder builder) {
        // TODO: configure and return the RestClient
    }
}
```

</details>

<details>
<summary>💡 Hint 2: RestClient Builder</summary>

The `RestClient.Builder` provides fluent methods:
- `.baseUrl(url)` - sets the base URL for all requests
- `.defaultHeader(name, value)` - adds default headers to all requests
- `.build()` - creates the RestClient instance

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

    @Bean
    public RestClient userRestClient(RestClient.Builder builder) {
        return builder
            .baseUrl(userServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
```

**Why use the injected `RestClient.Builder`?** Spring Boot auto-configures it with observability (tracing, metrics). Using `RestClient.builder()` directly would bypass this.

</details>

### 1.2 Add the Configuration Property

Add to `order-service/src/main/resources/application.yaml`:

```yaml
user:
  service:
    url: http://localhost:8081
```

**Verify:** Start order-service. If it starts without errors, the configuration is correct.

---

## Exercise 2: Create the UserClient

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
2. Inject the `userRestClient` bean (use `@Qualifier` to specify which RestClient)
3. Implement `getUserById()` using RestClient's fluent API

**Think about:**
- What HTTP method do you use to fetch a resource?
- How do you include the userId in the URL path?
- What happens if user-service returns 404? How should you handle it?

<details>
<summary>💡 Hint 1: Constructor injection with Qualifier</summary>

```java
@Component
public class UserClientImpl implements UserClient {

    private final RestClient restClient;

    public UserClientImpl(@Qualifier("userRestClient") RestClient restClient) {
        this.restClient = restClient;
    }
}
```

`@Qualifier` is needed because there could be multiple `RestClient` beans (one per service).

</details>

<details>
<summary>💡 Hint 2: Making a GET request</summary>

```java
restClient.get()
    .uri("/users/{id}", userId)  // {id} is replaced with userId
    .retrieve()
    .body(UserResponse.class);   // Deserialize JSON to UserResponse
```

</details>

<details>
<summary>💡 Hint 3: Handling 404 errors</summary>

Use `.onStatus()` to handle specific HTTP status codes before calling `.body()`:

```java
.onStatus(status -> status.value() == 404, (request, response) -> {
    throw new RuntimeException("User not found: " + userId);
})
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

## Exercise 3: Integrate into OrderService

Now use the `UserClient` in `OrderService` to validate users before creating orders.

### 3.1 Inject UserClient

Modify `OrderService.java`:
1. Add `UserClient` as a field
2. Add it to the constructor parameters

<details>
<summary>💡 Hint</summary>

```java
private final OrderRepository orderRepository;
private final UserClient userClient;

public OrderService(OrderRepository orderRepository, UserClient userClient) {
    this.orderRepository = orderRepository;
    this.userClient = userClient;
}
```

</details>

### 3.2 Validate User in createOrder

Update the `createOrder()` method to:
1. Call `userClient.getUserById()` with the request's userId
2. Log the user's name to verify it worked
3. Continue with order creation only if the user exists

**Think about:** What happens if `getUserById()` throws an exception? Does the order get created?

<details>
<summary>✅ Solution</summary>

```java
public Order createOrder(CreateOrderRequest request) {
    log.info("Creating order for user: " + request.userId());

    // Validate user exists (throws exception if not found)
    UserResponse user = userClient.getUserById(request.userId());
    log.info("User validated: " + user.firstName() + " " + user.lastName());

    // Create order only if user validation passed
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

### 3.3 Test It!

**Terminal 1:** Start user-service
```bash
cd user-service && mvn spring-boot:run
```

**Terminal 2:** Start order-service
```bash
cd order-service && mvn spring-boot:run
```

**Terminal 3:** Test the flow

First, create a user and save the returned ID:
```bash
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User","role":"EMPLOYEE"}'
```

Then create an order with that user ID:
```bash
curl -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "PASTE_USER_ID_HERE",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

✅ Check order-service logs - you should see: `User validated: Test User`

**Test with invalid user:**
```bash
curl -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "00000000-0000-0000-0000-000000000000",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

✅ This should return an error (500 with "User not found")

---

## Exercise 4: Add Resilience with Retry

Network calls can fail temporarily. Let's add automatic retry using Resilience4j.

### 4.1 Verify Dependency

Check that `order-service/pom.xml` has:
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

### 4.2 Configure Retry

Add to `application.yaml`:

```yaml
resilience4j:
  retry:
    instances:
      userService:
        maxAttempts: 3
        waitDuration: 500ms
```

This means: if a call fails, retry up to 3 times, waiting 500ms between attempts.

### 4.3 Apply Retry to the Client

Update `UserClientImpl.getUserById()`:

1. Add the `@Retry` annotation with the instance name
2. Create a fallback method that's called when all retries fail

<details>
<summary>💡 Hint</summary>

```java
@Override
@Retry(name = "userService", fallbackMethod = "getUserByIdFallback")
public UserResponse getUserById(UUID userId) {
    // existing code
}

private UserResponse getUserByIdFallback(UUID userId, Exception ex) {
    log.severe("Failed to fetch user " + userId + " after retries: " + ex.getMessage());
    throw new RuntimeException("User service is unavailable");
}
```

**Important:** The fallback method must have:
- Same return type
- Same parameters + an `Exception` parameter at the end

</details>

### 4.4 Test Retry

1. Start both services
2. Create an order (should work)
3. **Stop user-service** (Ctrl+C)
4. Try to create another order
5. Watch order-service logs - you'll see 3 retry attempts
6. After retries fail, the fallback is called

---

## Exercise 5: Add Timeout

Prevent requests from hanging forever if user-service is slow.

Add to `application.yaml`:

```yaml
resilience4j:
  timelimiter:
    instances:
      userService:
        timeoutDuration: 2s
```

**Test it:** Add `Thread.sleep(5000);` in user-service's `UserController.getUserById()`, then try to create an order. It should fail after 2 seconds.

---

## Exercise 6: Integration Test with Mock

Write a test that mocks `UserClient` so we don't need user-service running.

Create: `order-service/src/test/java/com/dornach/order/controller/OrderControllerIntegrationTest.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

    @Test
    void createOrder_ValidUser_ReturnsCreated() throws Exception {
        // TODO: Configure mock and test POST /orders
    }
}
```

<details>
<summary>✅ Solution</summary>

```java
package com.dornach.order.controller;

import com.dornach.order.client.UserClient;
import com.dornach.order.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserClient userClient;

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
            .andExpect(jsonPath("$.productName").value("Laptop"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
```

</details>

---

## Challenge: ShipmentClient (Optional)

Apply the same pattern to create a `ShipmentClient` for calling shipment-service:

1. Add `shipmentRestClient` bean in `RestClientConfig`
2. Create `ShipmentClient` interface
3. Create `ShipmentClientImpl` with RestClient
4. Add retry/timeout configuration for `shipmentService`
5. Create `confirmAndShipOrder()` in `OrderService` that:
   - Confirms the order
   - Calls shipment-service to create a shipment
   - Updates the order with the tracking number

---

## Validation Checklist

Before moving to Step 3, verify:

- [ ] `RestClientConfig` creates a configured `userRestClient` bean
- [ ] `UserClient` interface defines `getUserById(UUID)`
- [ ] `UserClientImpl` calls user-service via RestClient
- [ ] `POST /orders` validates user exists before creating order
- [ ] Logs show "User validated: {name}" on successful order creation
- [ ] Invalid userId returns an error (user not found)
- [ ] Retry works: stop user-service, see retry attempts in logs
- [ ] Tests pass: `mvn test`

---

## Summary

In this step, you learned:
- **RestClient** (Spring 6) provides a modern, fluent API for HTTP calls
- **Interface-based clients** improve testability and decoupling
- **Resilience4j** adds retry and timeout to handle network failures
- **Service orchestration** coordinates calls between microservices

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
