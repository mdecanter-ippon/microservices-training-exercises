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
- Bruno installed and configured (see Step 1)
- Basic understanding of HTTP clients
- Familiarity with dependency injection

---

## Context

The **order-service** needs to communicate with other services:
- **user-service** → Validate that the user exists before creating an order
- **shipment-service** → Create a shipment when the order is confirmed

```
┌─────────────┐      ┌──────────────┐      ┌─────────────────┐
│   Client    │ ──── │ order-service│ ──── │  user-service   │
└─────────────┘      │              │      └─────────────────┘
                     │              │      ┌─────────────────┐
                     │              │ ──── │shipment-service │
                     └──────────────┘      └─────────────────┘
```

---

## Exercise 1: Configure RestClient

**File:** `order-service/src/main/java/com/dornach/order/config/RestClientConfig.java`

Create a configuration class that provides `RestClient` beans for calling other services.

### 1.1 Create the Configuration Class

```java
@Configuration
public class RestClientConfig {

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${shipment.service.url}")
    private String shipmentServiceUrl;

    // TODO: Create RestClient beans
}
```

### 1.2 Create the User Service RestClient

```java
@Bean
public RestClient userRestClient(RestClient.Builder builder) {
    // TODO:
    // 1. Use the builder (not RestClient.builder() - important for tracing!)
    // 2. Set the base URL from userServiceUrl
    // 3. Add default Content-Type header (application/json)
    // 4. Return the built RestClient
}
```

<details>
<summary>💡 Hint</summary>

```java
@Bean
public RestClient userRestClient(RestClient.Builder builder) {
    return builder
        .baseUrl(userServiceUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
}
```

**Important:** Use the injected `RestClient.Builder`, not `RestClient.builder()`. This ensures tracing context is propagated (Step 7).

</details>

### 1.3 Configure the URLs

**File:** `order-service/src/main/resources/application.yaml`

```yaml
user:
  service:
    url: http://localhost:8081

shipment:
  service:
    url: http://localhost:8082
```

---

### 📘 Instructor Notes: Why One RestClient Per Service?

<details>
<summary>Click to expand</summary>

**Question fréquente des étudiants:** "Pourquoi ne pas avoir un seul RestClient générique qu'on réutilise partout ?"

**Réponse:** Créer un bean `RestClient` dédié par service externe offre plusieurs avantages :

1. **Isolation des configurations**
   - Chaque service peut avoir des timeouts différents (user-service : 2s, shipment-service : 5s)
   - Des headers spécifiques (authentification, API keys)
   - Des politiques de retry distinctes

2. **Base URL encapsulée**
   - Le client connaît son URL de base → les appelants utilisent juste `/users/{id}`
   - Changement d'URL = une seule modification dans la config

3. **Injection précise avec `@Qualifier`**
   ```java
   // Spring sait exactement quel RestClient injecter
   public UserClientImpl(@Qualifier("userRestClient") RestClient restClient)
   ```

4. **Traçabilité (Step 7)**
   - Dans les traces distribuées, chaque client apparaît distinctement
   - Facilite le debugging : "l'appel à user-service a échoué"

5. **Testabilité**
   - Chaque client peut être mocké indépendamment
   - Tests unitaires plus ciblés

6. **Évolution indépendante**
   - Ajouter de l'authentification à user-service sans toucher shipment-service
   - Migrer un service vers gRPC sans impacter les autres

**Analogie:** C'est comme avoir des télécommandes séparées pour la TV, la box et la barre de son plutôt qu'une télécommande universelle mal configurée.

**Anti-pattern à éviter:**
```java
// ❌ Mauvais : RestClient générique réutilisé partout
@Bean
public RestClient genericRestClient(RestClient.Builder builder) {
    return builder.build(); // Pas de base URL, pas de config spécifique
}

// Dans le service :
restClient.get().uri("http://localhost:8081/users/" + userId)... // URL en dur !
```

</details>

---

## Exercise 2: Create the UserClient Interface

**Files:**
- `order-service/src/main/java/com/dornach/order/client/UserClient.java`
- `order-service/src/main/java/com/dornach/order/client/UserClientImpl.java`

### 2.1 Define the Interface

```java
public interface UserClient {
    UserResponse getUserById(UUID userId);
}
```

**Question:** Why do we use an interface instead of calling RestClient directly in the service?

<details>
<summary>💡 Answer</summary>

1. **Testability** - Easy to mock in unit tests
2. **Decoupling** - Service doesn't depend on HTTP implementation
3. **Flexibility** - Can switch to gRPC, message queue, etc. without changing the service

</details>

### 2.2 Implement the Client

```java
@Component
@Slf4j
public class UserClientImpl implements UserClient {

    private final RestClient restClient;

    public UserClientImpl(@Qualifier("userRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        log.info("Fetching user: {}", userId);

        // TODO:
        // 1. Use restClient to GET /users/{userId}
        // 2. Return the UserResponse
        // 3. Handle 404 (throw UserNotFoundException)
    }
}
```

<details>
<summary>💡 Hint</summary>

```java
return restClient.get()
    .uri("/users/{id}", userId)
    .retrieve()
    .onStatus(status -> status.value() == 404, (request, response) -> {
        throw new UserNotFoundException(userId);
    })
    .body(UserResponse.class);
```

</details>

---

## Exercise 3: Add Resilience4j Retry

**File:** `order-service/src/main/resources/application.yaml`

Network calls can fail. Add retry configuration to handle transient failures.

### 3.1 Add Dependencies

Verify that `pom.xml` has:
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

### 3.2 Configure Retry

```yaml
resilience4j:
  retry:
    instances:
      userService:
        maxAttempts: ???        # How many times to retry?
        waitDuration: ???       # How long to wait between retries?
      shipmentService:
        maxAttempts: 3
        waitDuration: 500ms
```

<details>
<summary>💡 Hint</summary>

```yaml
resilience4j:
  retry:
    instances:
      userService:
        maxAttempts: 3
        waitDuration: 500ms
```

This means: retry up to 3 times, waiting 500ms between each attempt.

</details>

### 3.3 Apply Retry to the Client

Update `UserClientImpl`:

```java
@Override
@Retry(name = "userService", fallbackMethod = "getUserByIdFallback")
public UserResponse getUserById(UUID userId) {
    // ... existing code
}

private UserResponse getUserByIdFallback(UUID userId, Exception ex) {
    log.error("Failed to fetch user {} after retries: {}", userId, ex.getMessage());
    throw new ServiceUnavailableException("User service is unavailable");
}
```

---

## Exercise 4: Add Timeout

**File:** `order-service/src/main/resources/application.yaml`

Prevent requests from hanging forever.

```yaml
resilience4j:
  timelimiter:
    instances:
      userService:
        timeoutDuration: ???    # Max time to wait for response
      shipmentService:
        timeoutDuration: 2s
```

<details>
<summary>💡 Hint</summary>

```yaml
timelimiter:
  instances:
    userService:
      timeoutDuration: 2s
```

If the user-service doesn't respond within 2 seconds, the call fails.

</details>

---

## Exercise 5: Orchestrate in OrderService

**File:** `order-service/src/main/java/com/dornach/order/service/OrderService.java`

### 5.1 Inject the Client

```java
@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;

    // TODO: Add constructor injection
}
```

### 5.2 Validate User on Order Creation

```java
public Order createOrder(CreateOrderRequest request) {
    log.info("Creating order for user: {}", request.userId());

    // TODO:
    // 1. Call userClient.getUserById() to validate the user exists
    // 2. Log the user's name
    // 3. Create and save the order with PENDING status
    // 4. Return the saved order
}
```

<details>
<summary>💡 Hint</summary>

```java
public Order createOrder(CreateOrderRequest request) {
    log.info("Creating order for user: {}", request.userId());

    // Step 1: Validate user exists
    UserResponse user = userClient.getUserById(request.userId());
    log.info("User validated: {} {}", user.firstName(), user.lastName());

    // Step 2: Create order
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

**Validation:**
```bash
# Start user-service (Terminal 1)
cd user-service && mvn spring-boot:run

# Start order-service (Terminal 2)
cd order-service && mvn spring-boot:run

# Create an order (Terminal 3)
curl -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

---

## Exercise 6: Test with Mock

**File:** `order-service/src/test/java/com/dornach/order/controller/OrderControllerIntegrationTest.java`

Write an integration test that mocks the UserClient.

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
        // Arrange
        when(userClient.getUserById(any(UUID.class)))
            .thenReturn(new UserResponse(
                UUID.randomUUID(), "alice@test.com", "Alice", "Martin", "EMPLOYEE", "ACTIVE", null, null
            ));

        // Act & Assert
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

## Challenge: Implement ShipmentClient (Optional)

Create the `ShipmentClient` following the same pattern:
1. Interface `ShipmentClient`
2. Implementation `ShipmentClientImpl`
3. Resilience4j retry/timeout
4. Use it in `OrderService.confirmAndShipOrder()`

---

## Validation with Bruno & curl

There's no dedicated Bruno collection for Step 2 (inter-service communication is internal). Validation is done via curl and unit tests.

### Test the Order Flow

**Terminal 1:** Start user-service
```bash
cd user-service && mvn spring-boot:run
```

**Terminal 2:** Start order-service
```bash
cd order-service && mvn spring-boot:run
```

**Terminal 3:** Test the flow
```bash
# Create an order (should validate user first)
curl -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'

# Check order-service logs: should show "User validated: Alice Martin"
```

### Test Resilience (Retry)

```bash
# 1. Stop user-service (Ctrl+C in Terminal 1)
# 2. Try to create an order - it will retry 3 times then fail
# 3. Restart user-service
# 4. Try again - should succeed
```

### Test Timeout

Temporarily add a delay in user-service's `UserController.getUserById()`:
```java
Thread.sleep(5000); // 5 seconds delay
```
Then try to create an order - it should timeout after 2s.

---

## Validation Checklist

Before moving to Step 3, verify:

- [ ] `POST /orders` validates the user via user-service
- [ ] Check logs: "User validated: {name}" appears
- [ ] Invalid userId returns an error (user not found)
- [ ] Retry works: stop user-service, restart it, order creation eventually succeeds
- [ ] Timeout works: add a delay in user-service, order fails after 2s
- [ ] Tests pass with mocked UserClient

**Run the unit tests:**
```bash
cd order-service
mvn test
```

---

## Summary

In this step, you learned:
- **RestClient** (Spring 6) provides a fluent API for HTTP calls
- **Interface-based clients** improve testability and decoupling
- **Resilience4j** adds retry and timeout to handle failures
- **Orchestration** coordinates calls to multiple services

---

## Before Moving On

Make sure you're ready for Step 3:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 2"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-2-complete
```

**Next:** [Step 3 - Contract-First API](./STEP_3_CONTRACT_FIRST.md)
