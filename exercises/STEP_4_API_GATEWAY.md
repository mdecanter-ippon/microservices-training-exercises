# Step 4: API Gateway

---

## Recap: Step 3

In Step 3, you added **API documentation** with springdoc-openapi:
- **OpenApiConfig** with `@OpenAPIDefinition` for API metadata
- **@Tag** to group endpoints in Swagger UI
- **@Operation** and **@ApiResponse** annotations on endpoints
- **@ExampleObject** for request/response examples
- **Swagger UI** accessible at `/swagger-ui.html`
- **OpenAPI spec** exportable via `/v3/api-docs.yaml`

---

## Objectives

By the end of this exercise, you will:
- Understand why API Gateways are essential for microservices
- Configure LocalStack API Gateway HTTP v2
- Implement path-based routing to multiple services
- Configure rate limiting to protect backend services
- Test routing and throttling

---

## Prerequisites

- Step 3 completed (OpenAPI documentation working)
- **Docker** and **Docker Compose** installed
- **LocalStack Auth Token** (required for API Gateway feature)
- **awslocal** CLI installed
- **Bash shell** (for running setup scripts)

### Setup Instructions

**1. LocalStack Auth Token**

Set your LocalStack auth token as an environment variable before running `docker-compose`:

```bash
# Linux/macOS
export LOCALSTACK_AUTH_TOKEN="your-token-here"

# Windows PowerShell
$env:LOCALSTACK_AUTH_TOKEN="your-token-here"

# Windows CMD
set LOCALSTACK_AUTH_TOKEN=your-token-here
```

**2. Install awslocal CLI**

```bash
pip install awscli-local
```

**3. Bash Shell (Windows only)**

The setup scripts (`setup-gateway.sh`) require a bash shell. On Windows, use one of:
- **Git Bash** (recommended - comes with Git for Windows): Right-click in folder → "Git Bash Here"
- **WSL** (Windows Subsystem for Linux): `wsl --install` in PowerShell admin

---

## Context

Your clients (web, mobile) currently need to know each service's URL:
- `http://localhost:8081` for users
- `http://localhost:8082` for shipments
- `http://localhost:8083` for orders

This is problematic:
- **Security:** Multiple ports exposed
- **Management:** Clients need to track multiple URLs
- **Scaling:** Hard to add load balancing

**Solution:** An API Gateway provides a single entry point.

```
Before:                          After:
┌────────┐                       ┌────────┐
│ Client │──→ :8081 (users)      │ Client │
│        │──→ :8082 (shipments)  │        │
│        │──→ :8083 (orders)     └───┬────┘
└────────┘                           │
                                     ▼
                               ┌──────────┐
                               │ Gateway  │
                               │  :4566   │
                               └────┬─────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
         user-service        shipment-service       order-service
```

---

## Exercise 1: Start LocalStack

**File:** `docker-compose.yml`

### 1.1 Start LocalStack

```bash
docker-compose up -d localstack
```

### 1.2 Verify LocalStack is Running

```bash
# Check status
curl http://localhost:4566/_localstack/health

# Should show services including "apigateway"
```

### 1.3 Start All Services

In separate terminals:
```bash
# Terminal 1
cd user-service && mvn spring-boot:run

# Terminal 2
cd shipment-service && mvn spring-boot:run

# Terminal 3
cd order-service && mvn spring-boot:run
```

---

## Exercise 2: Create the API Gateway

**File:** `infra/setup-gateway.sh`

### 2.1 Examine the Setup Script

Read the existing script to understand what it does:
```bash
cat infra/setup-gateway.sh
```

Key steps:
1. Create an HTTP API (v2)
2. Create integrations (connections to backend services)
3. Create routes (path patterns)
4. Create a stage with throttling

### 2.2 Run the Setup Script

```bash
cd infra
./setup-gateway.sh
```

Note the **API_ID** that is displayed. Export it:
```bash
export DORNACH_API_ID=<your-api-id>
```

### 2.3 Understand the Gateway URL

The gateway URL format is:
```
http://localhost:4566/restapis/{API_ID}/{STAGE}/_user_request_/{path}
```

Create a shortcut:
```bash
export GATEWAY="http://localhost:4566/restapis/$DORNACH_API_ID/prod/_user_request_"
```

---

## Exercise 3: Test Routing

### 3.1 Test User Service Routing

```bash
# Direct call (bypass gateway)
curl http://localhost:8081/users

# Via gateway
curl $GATEWAY/users
```

Both should return the same result.

### 3.2 Test Other Services

```bash
# Shipments via gateway
curl $GATEWAY/shipments

# Create an order via gateway
curl -X POST $GATEWAY/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

### 3.3 Debug Routing Issues

If routing doesn't work:
```bash
# List all routes
awslocal apigatewayv2 get-routes --api-id $DORNACH_API_ID

# List all integrations
awslocal apigatewayv2 get-integrations --api-id $DORNACH_API_ID
```

---

## Exercise 4: Understand Path-Based Routing

### 4.1 Route Patterns

The setup script creates routes like:
```
ANY /users/{proxy+}    → http://host.docker.internal:8081/{proxy}
ANY /orders/{proxy+}   → http://host.docker.internal:8083/{proxy}
ANY /shipments/{proxy+} → http://host.docker.internal:8082/{proxy}
```

**Syntax:**
- `ANY` = all HTTP methods (GET, POST, PUT, DELETE)
- `{proxy+}` = capture everything after the prefix (greedy match)

### 4.2 Test Path Forwarding

```bash
# This request:
curl $GATEWAY/users/11111111-1111-1111-1111-111111111111

# Gets routed to:
# http://localhost:8081/users/11111111-1111-1111-1111-111111111111
```

**Question:** What happens if you call `$GATEWAY/unknown`?

<details>
<summary>💡 Answer</summary>

You get a **404 Not Found** from the gateway because there's no route matching `/unknown`.

</details>

---

## Exercise 5: Configure Rate Limiting

**File:** `infra/setup-gateway.sh`

### 5.1 Understand Throttling Settings

In the setup script, find the stage creation:
```bash
awslocal apigatewayv2 create-stage \
    --api-id $API_ID \
    --stage-name prod \
    --default-route-settings "ThrottlingBurstLimit=200,ThrottlingRateLimit=100"
```

**Parameters:**
- **RateLimit (100):** Average requests per second allowed
- **BurstLimit (200):** Maximum burst capacity (token bucket)

### 5.2 Test Rate Limiting

Send many requests quickly:
```bash
# Option 1: Simple bash loop
for i in {1..150}; do
  curl -s -o /dev/null -w "%{http_code}\n" $GATEWAY/users
done | sort | uniq -c

# Option 2: Apache Bench (if installed)
ab -n 200 -c 50 $GATEWAY/users
```

**Expected result:** After exceeding the rate limit, you'll see HTTP **429 Too Many Requests**.

### 5.3 Modify Rate Limits (Optional)

To test with lower limits:
```bash
# Delete existing stage
awslocal apigatewayv2 delete-stage \
    --api-id $DORNACH_API_ID \
    --stage-name prod

# Create with lower limits
awslocal apigatewayv2 create-stage \
    --api-id $DORNACH_API_ID \
    --stage-name prod \
    --default-route-settings "ThrottlingBurstLimit=10,ThrottlingRateLimit=5"
```

Now retry the test - you'll hit rate limits much faster.

---

## Challenge: Add Health Route (Optional)

Create a route that returns health status from all services:

1. Add a new route `/health` that points to any service's actuator
2. Test that `/health` returns status via the gateway

<details>
<summary>💡 Hint</summary>

```bash
# Create integration for health
HEALTH_INT=$(awslocal apigatewayv2 create-integration \
    --api-id $DORNACH_API_ID \
    --integration-type HTTP_PROXY \
    --integration-uri http://host.docker.internal:8081/actuator/health \
    --integration-method GET \
    --query 'IntegrationId' --output text)

# Create route
awslocal apigatewayv2 create-route \
    --api-id $DORNACH_API_ID \
    --route-key "GET /health" \
    --target "integrations/$HEALTH_INT"

# Test it
curl $GATEWAY/health
```

</details>

---

## Validation with Bruno

### Setup Bruno Environment

1. Open Bruno
2. Select the **Gateway** environment (bottom-left dropdown)
3. This environment uses the gateway URL instead of direct service URLs

### Run Step 4 Tests

Open the collection: **Step 4 - API Gateway**

Run these requests:
1. **List Users via Gateway** - Should return users list
2. **Create User via Gateway** - Should return 201
3. **Get User via Gateway** - Should return user details

All requests should work identically to direct calls.

### Manual curl Validation

```bash
# 1. Create user via gateway
curl -X POST $GATEWAY/users \
  -H "Content-Type: application/json" \
  -d '{"email":"gateway-test@dornach.com","firstName":"Gateway","lastName":"Test","role":"EMPLOYEE"}'

# 2. List users via gateway
curl $GATEWAY/users

# 3. Test shipments
curl $GATEWAY/shipments
```

---

## Validation Checklist

Before moving to Step 5, verify:

- [ ] LocalStack is running (`docker-compose up -d localstack`)
- [ ] Gateway setup script executes successfully
- [ ] `$GATEWAY/users` returns the same data as `localhost:8081/users`
- [ ] `$GATEWAY/orders` routes correctly to order-service
- [ ] `$GATEWAY/shipments` routes correctly to shipment-service
- [ ] Rate limiting works (429 returned when limit exceeded)
- [ ] Bruno "Step 4 - API Gateway" tests pass

---

## Summary

In this step, you learned:
- **API Gateway** provides a single entry point for multiple services
- **HTTP API v2** is lightweight and cost-effective
- **Path-based routing** directs requests to appropriate backends
- **Rate limiting** protects services from abuse (Token Bucket algorithm)
- **LocalStack** emulates AWS services locally for development

---

## Before Moving On

Make sure you're ready for Step 5:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 4"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-4-complete
```

**Next:** [Step 5 - H2M Authentication](./STEP_5_H2M_AUTHENTICATION.md)
