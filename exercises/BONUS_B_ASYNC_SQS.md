# Bonus B: Asynchronous Communication with SQS

---

## Recap: Bonus A

In Bonus A, you learned **integration testing** with Testcontainers:
- Real PostgreSQL and Keycloak containers for tests
- `@ServiceConnection` for auto-configuration
- Real JWT validation instead of mocks

Now let's add **asynchronous communication** to decouple our services.

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

### 1. Starting Point

- Bonus A completed (or Step 7 minimum)
- **Docker Desktop** running
- **Bruno** installed (https://www.usebruno.com/downloads)

If you need to catch up:
```bash
git stash && git checkout bonus-a-complete
```

### 2. Setup SQS Queues

Start LocalStack and create the queues:

```bash
# Start LocalStack
docker-compose up -d localstack

# Create SQS queues
# Linux/macOS/Git Bash:
./infra/setup-sqs.sh

# Windows PowerShell:
# .\infra\windows\setup-sqs.ps1
```

Verify the queues exist:
```bash
awslocal sqs list-queues
```

**Expected output:**
```json
{
    "QueueUrls": [
        "http://localhost:4566/000000000000/order-events",
        "http://localhost:4566/000000000000/order-events-dlq"
    ]
}
```

### 3. Add Spring Cloud AWS Dependencies

**File:** Parent `pom.xml` - Add BOM in `<dependencyManagement>`:

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-dependencies</artifactId>
    <version>3.2.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**File:** `order-service/pom.xml` - Add SQS dependency:

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-sqs</artifactId>
</dependency>
```

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

| Aspect | Synchronous | Asynchronous |
|--------|-------------|--------------|
| Response time | Slow (waits for all) | Fast |
| Coupling | Strong | Weak |
| Resilience | If notif down → error | Messages queue up |
| Retry | Manual | Automatic (DLQ) |

---

## Exercise 1: Configure order-service as Event Publisher

In this exercise, you'll configure order-service to publish events to SQS when an order is created.

### 1.1 Add AWS Configuration

**File:** `order-service/src/main/resources/application.yaml`

Add this configuration:

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

### 1.2 Create the Event DTO

**File:** `order-service/src/main/java/com/dornach/order/event/OrderCreatedEvent.java`

```java
package com.dornach.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

### 1.3 Create the Event Publisher

**File:** `order-service/src/main/java/com/dornach/order/event/OrderEventPublisher.java`

```java
package com.dornach.order.event;

import com.dornach.order.domain.Order;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

### 1.4 Integrate Publisher into OrderService

**File:** `order-service/src/main/java/com/dornach/order/service/OrderService.java`

Add the publisher as a dependency and call it after saving the order:

```java
@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ShipmentClient shipmentClient;
    private final OrderEventPublisher eventPublisher;  // Add this

    // Update constructor to include eventPublisher

    public Order createOrder(CreateOrderRequest request) {
        // ... existing validation and order creation ...

        Order saved = orderRepository.save(order);

        // Publish event asynchronously
        eventPublisher.publishOrderCreated(saved);

        return saved;
    }
}
```

<details>
<summary>💡 Key concept: Fire and Forget</summary>

`sqsTemplate.send()` is non-blocking. The message is sent to SQS and the method returns immediately. The order API response doesn't wait for notification-service to process the message.

This is the "fire and forget" pattern - we trust SQS to deliver the message reliably.

</details>

---

## Exercise 2: Create notification-service

In this exercise, you'll create a new microservice that consumes order events from SQS.

### 2.1 Create Module Structure

Create the following directory structure:

```
notification-service/
├── pom.xml
└── src/main/
    ├── java/com/dornach/notification/
    │   ├── NotificationServiceApplication.java
    │   ├── listener/OrderEventListener.java
    │   └── dto/OrderCreatedEvent.java
    └── resources/
        └── application.yaml
```

### 2.2 Create pom.xml

**File:** `notification-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

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

### 2.3 Create Application Class

**File:** `notification-service/src/main/java/com/dornach/notification/NotificationServiceApplication.java`

```java
package com.dornach.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

### 2.4 Create application.yaml

**File:** `notification-service/src/main/resources/application.yaml`

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

### 2.5 Create Event DTO

**File:** `notification-service/src/main/java/com/dornach/notification/dto/OrderCreatedEvent.java`

```java
package com.dornach.notification.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

### 2.6 Create Event Listener

**File:** `notification-service/src/main/java/com/dornach/notification/listener/OrderEventListener.java`

```java
package com.dornach.notification.listener;

import com.dornach.notification.dto.OrderCreatedEvent;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

### 2.7 Test the Flow

**Start the services:**

```bash
# Terminal 1: notification-service
cd notification-service && mvn spring-boot:run

# Terminal 2: order-service (with other services running)
cd order-service && mvn spring-boot:run
```

**Create an order:**

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

**Expected output in notification-service logs:**
```
Received order event: abc-123...
Product: Laptop x1
Calling legacy notification system...
Notification sent for order: abc-123...
Order event processed successfully!
```

<details>
<summary>💡 Understanding @SqsListener</summary>

| Feature | Description |
|---------|-------------|
| Auto-configuration | Sets up the SQS consumer automatically |
| Deserialization | JSON → Java object (uses Jackson) |
| Acknowledgement | Automatic ACK if no exception thrown |
| Retry | Automatic retry on exception |

</details>

---

## Exercise 3: Test Dead Letter Queue

In this exercise, you'll understand how failed messages are handled by the Dead Letter Queue.

### 3.1 Make the Consumer Fail

Temporarily modify `OrderEventListener` to throw an exception:

**File:** `notification-service/src/main/java/com/dornach/notification/listener/OrderEventListener.java`

```java
@SqsListener("order-events")
public void handleOrderCreatedEvent(OrderCreatedEvent event) {
    log.info("Received order event: {}", event.orderId());
    throw new RuntimeException("Simulated failure!");
}
```

### 3.2 Send a Message and Observe Retries

Create another order (same curl command as before).

In notification-service logs, you'll see **3 processing attempts**:
```
Received order event: xyz-456...
ERROR: Simulated failure!
Received order event: xyz-456...
ERROR: Simulated failure!
Received order event: xyz-456...
ERROR: Simulated failure!
```

### 3.3 Check the Dead Letter Queue

After 3 retries, the message goes to the DLQ:

```bash
awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/order-events-dlq \
  --max-number-of-messages 10
```

You should see the failed message with order details in JSON format.

### 3.4 Restore Normal Behavior

Remove the `throw` statement and restart notification-service:

```java
@SqsListener("order-events")
public void handleOrderCreatedEvent(OrderCreatedEvent event) {
    log.info("Received order event: {}", event.orderId());
    log.info("Product: {} x{}", event.productName(), event.quantity());
    simulateLegacyNotificationCall(event);
    log.info("Order event processed successfully!");
}
```

<details>
<summary>💡 Why Dead Letter Queues matter</summary>

Without a DLQ:
- Poison messages (that always fail) block the queue forever
- You lose visibility into failures
- No way to analyze or reprocess failed messages

With a DLQ:
- Failed messages are moved aside after N retries
- Main queue continues processing new messages
- Operations team can investigate and reprocess DLQ messages

</details>

---

## Challenge: Verify Trace Propagation (Optional)

Check if the trace ID is propagated through SQS:

1. Create an order
2. Open Zipkin (http://localhost:9411)
3. Find the trace
4. Verify it shows: `order-service → SQS → notification-service`

**Hint:** You may need to add tracing dependencies to notification-service.

---

## Validation Checklist

Before moving on, verify:

- [ ] SQS queues created (order-events + order-events-dlq)
- [ ] order-service publishes events on order creation
- [ ] notification-service receives and processes events
- [ ] Logs show asynchronous processing (order returns before notification completes)
- [ ] DLQ receives failed messages after 3 retries

---

## Troubleshooting

### "Queue does not exist"

```bash
# Verify LocalStack is running
docker ps | grep localstack

# Recreate queues
./infra/setup-sqs.sh
```

### "Connection refused to localhost:4566"

LocalStack is not started:
```bash
docker-compose up -d localstack
```

### Messages not received by notification-service

1. Check queue name matches in `@SqsListener("order-events")`
2. Verify AWS config is identical in both services
3. Ensure notification-service started successfully

---

## Summary

In this exercise, you learned:

| Concept | What You Did |
|---------|--------------|
| **SQS** | Used reliable message queue for async communication |
| **Fire and forget** | Published events without waiting for response |
| **@SqsListener** | Consumed messages with auto-configuration |
| **Dead Letter Queue** | Handled failed messages gracefully |
| **LocalStack** | Emulated AWS SQS locally |

---

## Before Moving On

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Bonus B"
```

**Option B:** You need to catch up
```bash
git stash && git checkout bonus-b-complete
```

**Next:** [Bonus C - MapStruct](./BONUS_C_MAPSTRUCT.md)
