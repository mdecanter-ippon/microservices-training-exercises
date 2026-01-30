# Step 7: Distributed Tracing

---

## Recap: Step 6

In Step 6, you implemented **M2M (Machine-to-Machine) authentication**:
- **Client Credentials flow** for service-to-service calls
- **order-service-client** confidential client in Keycloak
- **OAuth2AuthorizedClientManager** for automatic token lifecycle
- **OAuth2ClientHttpRequestInterceptor** to inject tokens into RestClient calls
- **RBAC with @PreAuthorize** using `service-caller` role
- Services can now authenticate as themselves, not on behalf of users

---

## Objectives

By the end of this exercise, you will:
- Understand why distributed tracing is essential for microservices
- Configure Micrometer Tracing with Zipkin
- Trace requests across multiple services
- Correlate logs with trace IDs
- Analyze traces to debug performance issues

---

## Prerequisites

- Step 6 completed (M2M authentication working)
- **Bruno** installed (https://www.usebruno.com/downloads)
- Docker and Docker Compose installed
- All services running

---

## Context

In a microservices architecture, a single request can span multiple services. Without tracing, debugging becomes nearly impossible:

```
[order-service] Processing order...
[user-service] Fetching user...
[shipment-service] Creating shipment...
```

**Questions you can't answer:**
- Are these logs from the same request?
- Which service caused the 500 error?
- Why did this request take 3 seconds?

**With Distributed Tracing:**
```
[order-service,abc123,span1] Processing order...
[user-service,abc123,span2] Fetching user...
[shipment-service,abc123,span3] Creating shipment...
              ^^^^^^
         Same Trace ID = same request!
```

---

## Exercise 1: Start Zipkin

### 1.1 Start Zipkin Container

```bash
docker-compose up -d zipkin
```

### 1.2 Verify Zipkin is Running

Open http://localhost:9411 in your browser.

You should see the Zipkin UI with a search form.

---

## Exercise 2: Add Tracing Dependencies

**File:** Parent `pom.xml` (root of project)

### 2.1 Add Dependencies to Parent POM

Add these dependencies to the **parent pom.xml** (not individual services). This ensures all services inherit them:

```xml
<!-- Distributed Tracing -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

Add them in the `<dependencies>` section, after the existing dependencies.

> **Note:** Adding to the parent POM means all services automatically get these dependencies without modifying each service's pom.xml.

---

## Exercise 3: Configure Tracing

**Files:** `application.yaml` in all services

### 3.1 Add Tracing Configuration

```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # Trace 100% of requests (dev only)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

<details>
<summary>💡 Explanation</summary>

- `sampling.probability: 1.0` - Trace every request (reduce in production)
- `zipkin.tracing.endpoint` - Where to send trace data

</details>

### 3.2 Verify Log Correlation Pattern

Check that this pattern is present in each service's `application.yaml` (it may already be there):

```yaml
logging:
  pattern:
    correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

This adds service name, trace ID, and span ID to every log line.

> **Note:** If already present, no changes needed. The pattern enables log correlation with traces.

### 3.3 Restart All Services

```bash
# Restart each service to apply configuration
cd user-service && mvn spring-boot:run
cd shipment-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
```

---

## Exercise 4: Generate Your First Trace

### 4.1 Make a Simple Request

```bash
# Get a token first
TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password' | jq -r '.access_token')

# Make a request to user-service
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users
```

### 4.2 Find the Trace in Zipkin

1. Open http://localhost:9411
2. Click **"Run Query"** (default settings)
3. Find the trace with `serviceName: user-service`
4. Click on it to see details

**What you should see:**
- Single span: `GET /users`
- Duration: ~20-50ms
- Tags: `http.method`, `http.url`, `http.status_code`

---

## Exercise 5: Distributed Trace (Multi-Service)

### 5.1 Create an Order (Triggers Multiple Services)

```bash
curl -X POST http://localhost:8083/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

### 5.2 Find the Distributed Trace

1. Open http://localhost:9411
2. Select **serviceName: order-service**
3. Click **"Run Query"**
4. Find the trace with multiple services

**What you should see:**
```
Timeline (Waterfall View):
|--- order-service: POST /orders (250ms total) ---|
  |-- user-service: GET /users/{id} (30ms) --|
                                              |-- shipment-service: POST /shipments (80ms) --|
```

### 5.3 Analyze the Trace

Click on each span to see:
- **Duration:** How long each service took
- **Tags:** HTTP method, URL, status code
- **Service:** Which service handled this span
- **Parent:** Which span called this one

**Question:** Are the calls to user-service and shipment-service sequential or parallel?

<details>
<summary>💡 Answer</summary>

They are **sequential**. First order-service validates the user, then creates the shipment. You can see this in the timeline - they don't overlap.

</details>

---

## Exercise 6: Correlate Logs with Traces

### 6.1 Check Service Logs

Look at the logs from each service:

```bash
# user-service logs
# [user-service,abc123,span2] Fetching user 11111111-1111-1111-1111-111111111111

# order-service logs
# [order-service,abc123,span1] Creating order for user 11111111...
# [order-service,abc123,span1] User validated: Alice Martin
```

### 6.2 Find All Logs for a Trace

Given a Trace ID from Zipkin:
```bash
# Example: trace ID is abc123def456
grep "abc123def456" logs/*.log
```

All matching logs are from the same request, across all services.

---

## Exercise 7: Debug an Error with Tracing

### 7.1 Simulate an Error

Stop shipment-service:
```bash
# Stop shipment-service (Ctrl+C or docker stop)
```

### 7.2 Try to Create an Order

```bash
curl -X POST http://localhost:8083/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Test",
    "quantity": 1,
    "totalPrice": 49.99,
    "shippingAddress": "456 Error St"
  }'

# Should return an error
```

### 7.3 Analyze the Failed Trace

1. Open Zipkin
2. Run query with defaults
3. Look for traces with errors (usually highlighted in red)
4. Open the trace

**What you'll see:**
- `order-service` span: OK
- `user-service` span: OK
- `shipment-service` span: **ERROR**
  - Tag: `error=true`
  - Tag: `error.message=Connection refused`

**Now you know:** The error was in shipment-service!

### 7.4 Restart shipment-service

```bash
cd shipment-service && mvn spring-boot:run
```

---

## Challenge: Custom Span Tags (Optional)

Add business-specific tags to your traces:

```java
@Service
public class OrderService {

    private final Tracer tracer;

    public Order createOrder(CreateOrderRequest request) {
        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("order.items_count", String.valueOf(1));
            span.tag("order.total_price", String.valueOf(request.totalPrice()));
        }
        // ... rest of logic
    }
}
```

These tags appear in Zipkin and help with debugging business logic.

---

## Validation with Bruno

### Run Step 7 Tests

1. Open Bruno
2. Select the **Direct** environment
3. Navigate to **Step 7 - Distributed Tracing**

Run the "Create Full Order" request, then:
1. Open Zipkin
2. Find the trace
3. Verify it shows all three services

<details>
<summary><strong>Bruno Collection Reference - Step 7</strong></summary>

### Recommended Test Sequence

| # | Request | Method | URL | Description |
|---|---------|--------|-----|-------------|
| 1 | Get Alice Token | POST | `/realms/dornach/.../token` | Get H2M token for Alice, saved to `alice_token` |
| 2 | Get Bob Token | POST | `/realms/dornach/.../token` | Get H2M token for Bob (admin), saved to `bob_token` |
| 3 | Create Test User | POST | `/users` | Create test user for tracing, ID saved to `user_id` |
| 4 | Simple Trace - Get User | GET | `/users/{id}` | Single-service trace (user-service only) |
| 5 | Distributed Trace - Create Order | POST | `/orders` | Multi-service trace: order → user → shipment |
| 6 | Check Zipkin Health | GET | `/health` | Verify Zipkin is running |

**Key tests validated:**
- Single-service trace visible in Zipkin
- Distributed trace across 3 services with same Trace ID
- Log correlation with traceId/spanId
- Automatic context propagation via RestClient

**After running tests:**
1. Open Zipkin: http://localhost:9411
2. Select `serviceName: order-service`
3. Click "Run Query"
4. Find trace with multiple spans
5. Click to see distributed trace timeline

**Expected trace for Create Order:**
```
order-service (parent span)
├── user-service (validate user)
└── shipment-service (create shipment)
```

**Prerequisites:**
1. Zipkin running: `docker-compose up -d zipkin`
2. All services running with tracing config
3. Keycloak configured (for tokens)

**Environment variables used:**
- `alice_token`, `bob_token`: JWT tokens
- `user_id`: Test user ID for tracing

</details>

---

## Validation Checklist

Before moving to Bonus exercises, verify:

- [ ] Zipkin is running at http://localhost:9411
- [ ] All services have tracing configuration
- [ ] Logs show `[serviceName,traceId,spanId]` format
- [ ] Simple request creates a trace visible in Zipkin
- [ ] Order creation creates a distributed trace with 3+ spans
- [ ] All spans in a distributed trace share the same Trace ID
- [ ] Error traces show the failing service clearly
- [ ] Bruno "Step 7 - Distributed Tracing" tests pass

---

## Summary

In this step, you learned:
- **Trace ID** uniquely identifies a request across all services
- **Span** represents a single operation (HTTP call, DB query)
- **Micrometer Tracing** auto-configures with Spring Boot
- **Context propagation** happens automatically via `traceparent` header
- **Zipkin** visualizes traces with timelines and spans
- **Log correlation** links logs to traces via Trace ID

---

## Key Concepts

| Concept | Description |
|---------|-------------|
| **Trace** | Complete journey of a request through all services |
| **Span** | Single operation within a trace |
| **Trace ID** | Unique identifier shared by all spans in a trace |
| **Span ID** | Unique identifier for a single span |
| **Parent Span** | The span that called this span |
| **Sampling** | Percentage of requests to trace (1.0 = 100%) |

---

## Before Moving On

Make sure you're ready for Bonus exercises:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 7"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-7-complete
```

**Next:** [Bonus A - Testcontainers](./BONUS_A_TESTCONTAINERS.md) or [Bonus B - Async SQS](./BONUS_B_ASYNC_SQS.md)
