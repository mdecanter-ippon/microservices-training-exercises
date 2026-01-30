# Bonus B: Asynchronous Communication with SQS

> **⚠️ Before starting:** Make sure you have completed Bonus A (or Step 7 minimum).
> If you need to catch up:
> ```bash
> git stash && git checkout bonus-a-complete
> ```

---

## Objectives

By the end of this exercise, you will:
- Understand the benefits of asynchronous communication
- Configure LocalStack SQS queues
- Publish messages from order-service
- Consume messages in notification-service
- Handle failures with Dead Letter Queues (DLQ)

---

## Prerequisites

- Bonus A completed (or Step 7)
- **Bruno** installed (https://www.usebruno.com/downloads)
- LocalStack running
- Basic understanding of message queues

---

## Context

Currently, when an order is created:
1. order-service validates the user (sync)
2. order-service creates a shipment (sync)
3. Returns immediately to the client

**Problem:** What if we need to send notifications? Email? SMS?
- Synchronous calls slow down the response
- If notification fails, should the order fail too?

**Solution: Asynchronous Communication**
```
POST /orders (sync)
     │
     ├─→ user-service (sync) ✓
     ├─→ shipment-service (sync) ✓
     │
     └─→ SQS Queue ──→ notification-service (async)
          "Fire and forget"    Processes later
```

**Benefits:**
- Order response is fast (doesn't wait for notification)
- If notification-service is down, messages queue up
- Automatic retries with Dead Letter Queue

---

## Exercise 1: Setup SQS Queues

### 1.1 Start LocalStack

```bash
docker-compose up -d localstack
```

### 1.2 Run SQS Setup Script

**Linux/macOS/Git Bash:**
```bash
./infra/setup-sqs.sh
```

**Windows PowerShell:**
```powershell
.\infra\windows\setup-sqs.ps1
```

This creates:
- `order-events` - Main queue for order events
- `order-events-dlq` - Dead Letter Queue for failed messages

### 1.3 Verify Queues

```bash
# List queues
awslocal sqs list-queues

# Should show:
# order-events
# order-events-dlq
```

---

## Exercise 2: Add Spring Cloud AWS Dependencies

**File:** Parent `pom.xml`

### 2.1 Add BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.awspring.cloud</groupId>
            <artifactId>spring-cloud-aws-dependencies</artifactId>
            <version>3.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2.2 Add SQS Dependency to order-service

**File:** `order-service/pom.xml`

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
```

---

## Exercise 3: Configure order-service as Publisher

**File:** `order-service/src/main/resources/application.yaml`

### 3.1 Add AWS Configuration

```yaml
spring:
  cloud:
    aws:
      region:
        static: us-east-1
      credentials:
        access-key: test
        secret-key: test
      sqs:
        endpoint: http://localhost:4566

app:
  sqs:
    order-events-queue: order-events
```

### 3.2 Create Event DTO

**File:** `order-service/src/main/java/com/dornach/order/event/OrderCreatedEvent.java`

```java
public record OrderCreatedEvent(
    UUID orderId,
    UUID userId,
    String productName,
    int quantity,
    BigDecimal totalPrice,
    String shippingAddress,
    String trackingNumber,
    Instant createdAt
) {}
```

### 3.3 Create Event Publisher

**File:** `order-service/src/main/java/com/dornach/order/event/OrderEventPublisher.java`

```java
@Component
@Slf4j
public class OrderEventPublisher {

    private final SqsTemplate sqsTemplate;
    private final String queueName;

    public OrderEventPublisher(
            SqsTemplate sqsTemplate,
            @Value("${app.sqs.order-events-queue}") String queueName) {
        this.sqsTemplate = sqsTemplate;
        this.queueName = queueName;
    }

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getUserId(),
            order.getProductName(),
            order.getQuantity(),
            order.getTotalPrice(),
            order.getShippingAddress(),
            order.getTrackingNumber(),
            order.getCreatedAt()
        );

        sqsTemplate.send(queueName, event);
        log.info("Published order event to queue: {}", queueName);
    }
}
```

### 3.4 Publish Event in OrderService

**File:** `order-service/src/main/java/com/dornach/order/service/OrderService.java`

Add the publisher call after order creation:

```java
@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ShipmentClient shipmentClient;
    private final OrderEventPublisher eventPublisher;  // Add this

    public Order createOrder(CreateOrderRequest request) {
        // ... existing validation and order creation ...

        Order saved = orderRepository.save(order);

        // Publish event asynchronously
        eventPublisher.publishOrderCreated(saved);

        return saved;
    }
}
```

---

## Exercise 4: Create notification-service

### 4.1 Create Module Structure

```
notification-service/
├── pom.xml
├── Dockerfile
└── src/main/
    ├── java/com/dornach/notification/
    │   ├── NotificationServiceApplication.java
    │   ├── listener/OrderEventListener.java
    │   └── dto/OrderCreatedEvent.java
    └── resources/
        └── application.yaml
```

### 4.2 Create pom.xml

```xml
<project>
    <parent>
        <groupId>com.dornach</groupId>
        <artifactId>microservices-training</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>notification-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>io.awspring.cloud</groupId>
            <artifactId>spring-cloud-aws-starter-sqs</artifactId>
        </dependency>
    </dependencies>
</project>
```

### 4.3 Create Application Class

```java
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

### 4.4 Create application.yaml

```yaml
server:
  port: 8084

spring:
  application:
    name: notification-service
  cloud:
    aws:
      region:
        static: us-east-1
      credentials:
        access-key: test
        secret-key: test
      sqs:
        endpoint: http://localhost:4566
```

### 4.5 Create Event Listener

**File:** `notification-service/src/main/java/com/dornach/notification/listener/OrderEventListener.java`

```java
@Component
@Slf4j
public class OrderEventListener {

    @SqsListener("order-events")
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        log.info("Received order event: {}", event.orderId());
        log.info("Product: {} x{}", event.productName(), event.quantity());

        // Simulate legacy notification system call
        simulateLegacyNotificationCall(event);

        log.info("Order event processed successfully!");
    }

    private void simulateLegacyNotificationCall(OrderCreatedEvent event) {
        try {
            log.info("Calling legacy notification system...");
            Thread.sleep(500);  // Simulate network latency
            log.info("Notification sent for order: {}", event.orderId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Notification failed", e);
        }
    }
}
```

---

## Exercise 5: Test the Flow

### 5.1 Start Services

```bash
# Terminal 1: notification-service
cd notification-service && mvn spring-boot:run

# Terminal 2: order-service (and dependencies)
cd order-service && mvn spring-boot:run
```

### 5.2 Create an Order

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=bob' \
  -d 'password=bob123' \
  -d 'grant_type=password' | jq -r '.access_token')

curl -X POST http://localhost:8083/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "22222222-2222-2222-2222-222222222222",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

### 5.3 Check notification-service Logs

You should see:
```
[notification-service] Received order event: abc-123...
[notification-service] Product: Laptop x1
[notification-service] Calling legacy notification system...
[notification-service] Notification sent for order: abc-123...
[notification-service] Order event processed successfully!
```

---

## Exercise 6: Test Dead Letter Queue

### 6.1 Make the Consumer Fail

Temporarily modify `OrderEventListener` to throw an exception:

```java
@SqsListener("order-events")
public void handleOrderCreatedEvent(OrderCreatedEvent event) {
    log.info("Received order event: {}", event.orderId());
    throw new RuntimeException("Simulated failure!");
}
```

### 6.2 Send a Message

Create another order (same curl command as before).

### 6.3 Check DLQ

After 3 retries (configurable), the message goes to DLQ:

```bash
awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/order-events-dlq \
  --max-number-of-messages 10
```

You should see the failed message in the DLQ.

### 6.4 Revert the Change

Remove the `throw` statement and restart notification-service.

---

## Challenge: Verify Trace Propagation (Optional)

Check if the trace ID is propagated through SQS:

1. Create an order
2. Open Zipkin (http://localhost:9411)
3. Find the trace
4. Verify it shows: `order-service → SQS → notification-service`

---

## Validation with Bruno

### Run Bonus B Tests

1. Open Bruno
2. Select the **Direct** environment
3. Navigate to **Bonus B - Async SQS**

Run "Create Order (triggers SQS)" and verify:
1. Order is created (201)
2. notification-service logs show the event processed

---

## Validation Checklist

Before completing the training, verify:

- [ ] SQS queues created (order-events + order-events-dlq)
- [ ] order-service publishes events to SQS
- [ ] notification-service receives and processes events
- [ ] Logs show async message processing
- [ ] DLQ receives failed messages after 3 retries
- [ ] (Optional) Trace ID propagated through SQS

---

## Summary

In this exercise, you learned:
- **SQS** provides reliable asynchronous messaging
- **Fire and forget** pattern decouples services
- **@SqsListener** auto-configures message consumers
- **Dead Letter Queue** handles poison messages
- **LocalStack** emulates AWS services locally

---

<details>
<summary><strong>Bruno Collection Reference - Bonus B</strong></summary>

### Test Sequence

| # | Request | Method | URL | Description |
|---|---------|--------|-----|-------------|
| 1 | Create Order (PENDING) | POST | `/orders` | Create order in PENDING status (no SQS event yet) |
| 2 | Confirm Order (triggers SQS) | POST | `/orders/{id}/confirm` | Confirm order - triggers SQS event to notification-service |
| 3 | Notification Service Health | GET | `/actuator/health` | Verify notification-service is running |
| 4 | Check SQS Queue Stats | GET | LocalStack debug endpoint | Check messages in SQS queue |

**Key tests validated:**
- Two-step order flow: create (PENDING) → confirm (SHIPPED)
- SQS event published only on confirmation
- Async processing by notification-service
- Dead Letter Queue for failed messages

**Expected flow:**
```
1. Create Order → PENDING (sync)
2. Confirm Order → SHIPPED (sync)
   └── Publish to SQS (async)
       └── notification-service consumes event
           └── Logs notification or sends email
```

**Prerequisites:**
1. LocalStack running with SQS: `docker-compose up -d localstack`
2. SQS queue created: `./infra/setup-sqs.sh`
3. notification-service running
4. Bob's token available (from Step 5)

**Environment variables used:**
- `order_service_url`: Order service URL
- `bob_token`: Admin JWT token
- `alice_user_id`: Test user ID
- `order_id`: Created order ID (auto-filled)

**CLI commands for debugging:**
```bash
# List queues
awslocal sqs list-queues

# Check message count
awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/order-events \
  --attribute-names ApproximateNumberOfMessages

# Check Dead Letter Queue
awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/order-events-dlq
```

</details>

---

## Congratulations!

You have completed the Microservices Training! You now have hands-on experience with:

| Step | Topic |
|------|-------|
| 1 | REST APIs with Java 21 |
| 2 | Service Communication |
| 3 | Contract-First with OpenAPI |
| 4 | API Gateway |
| 5 | H2M Authentication |
| 6 | M2M Authentication |
| 7 | Distributed Tracing |
| Bonus A | Testcontainers |
| Bonus B | Async SQS |

---

## Before Finishing

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Bonus B - Training Complete!"
```

**Option B:** You want to see the final solution
```bash
git stash && git checkout bonus-b-complete
```
