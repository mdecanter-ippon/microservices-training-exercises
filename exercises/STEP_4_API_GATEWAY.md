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
- **AWS CLI** installed (https://aws.amazon.com/cli/)

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

**2. Install AWS CLI**

- **Windows:** Download from https://aws.amazon.com/cli/ or `winget install Amazon.AWSCLI`
- **macOS:** `brew install awscli`
- **Linux:** `pip install awscli` or use your package manager

**3. Install awslocal (Linux/macOS only)**

For bash scripts on Linux/macOS:
```bash
pip install awscli-local
```

> **Note:** Windows users don't need `awslocal` - the PowerShell script uses `aws --endpoint-url` directly.

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

> **Why separate terminals instead of Docker?** We deliberately run services on the host machine rather than in Docker containers. Running all services in Docker would require complex network configuration (Docker networks, service discovery, DNS resolution between containers). By running services locally, the API Gateway in LocalStack can reach them via `host.docker.internal`, which simplifies the setup significantly.

---

## Exercise 2: Create the API Gateway

**Files:**
- `infra/setup-gateway.sh` (Linux/macOS/Git Bash)
- `infra/windows/setup-gateway.ps1` (Windows PowerShell)

### 2.1 Understand the Setup Script

The script automates the API Gateway configuration using `awslocal` (LocalStack AWS CLI):

| Step | AWS Command | Description |
|------|-------------|-------------|
| 1 | `create-api` | Creates an HTTP API (v2) named "dornach-gateway" |
| 2 | `create-integration` | Creates 3 HTTP_PROXY integrations pointing to each service URL |
| 3 | `create-route` | Maps URL paths to integrations: `/users/*`, `/orders/*`, `/shipments/*` |
| 4 | `create-stage` | Creates a "prod" stage with auto-deploy enabled |

**Why HTTP API v2?**
- 71% cheaper than REST API v1
- Lower latency (~10ms vs ~20ms)
- Simpler configuration for basic routing

### 2.2 Run the Setup Script

**Linux/macOS/Git Bash:**
```bash
cd infra
./setup-gateway.sh
```

**Windows PowerShell:**
```powershell
cd infra\windows
.\setup-gateway.ps1
```

Note the **API_ID** that is displayed. Export it:
```bash
# Linux/macOS/Git Bash
export DORNACH_API_ID=<your-api-id>

# Windows PowerShell
$env:DORNACH_API_ID="<your-api-id>"
```

### 2.3 Understand the Gateway URL

The gateway URL format is:
```
http://localhost:4566/restapis/{API_ID}/{STAGE}/_user_request_/{path}
```

Create a shortcut:
```bash
# Linux/macOS/Git Bash
export GATEWAY="http://localhost:4566/restapis/$DORNACH_API_ID/prod/_user_request_"

# Windows PowerShell
$env:GATEWAY="http://localhost:4566/restapis/$env:DORNACH_API_ID/prod/_user_request_"
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

The setup script creates routes with path forwarding:
```
ANY /users           → http://host.docker.internal:8081/users
ANY /users/{proxy+}  → http://host.docker.internal:8081/users/* (via path mapping)
ANY /orders          → http://host.docker.internal:8083/orders
ANY /orders/{proxy+} → http://host.docker.internal:8083/orders/* (via path mapping)
ANY /shipments           → http://host.docker.internal:8082/shipments
ANY /shipments/{proxy+}  → http://host.docker.internal:8082/shipments/* (via path mapping)
```

**Syntax:**
- `ANY` = all HTTP methods (GET, POST, PUT, DELETE)
- `{proxy+}` = capture everything after the prefix (greedy match)
- Path mapping (`overwrite:path=$request.path`) forwards the full request path to backend

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

## Exercise 5: Rate Limiting (Concept)

### 5.1 Understand Throttling Settings

In a production AWS environment, you would configure rate limiting on the stage:
```bash
awslocal apigatewayv2 create-stage \
    --api-id $API_ID \
    --stage-name prod \
    --default-route-settings "ThrottlingBurstLimit=200,ThrottlingRateLimit=100"
```

**Parameters:**
- **RateLimit (100):** Average requests per second allowed
- **BurstLimit (200):** Maximum burst capacity (token bucket)

> **Note:** Rate limiting requires LocalStack Pro. The current setup script creates a basic stage without throttling. In production AWS, throttling is automatically available.

### 5.2 How Rate Limiting Works

The **Token Bucket Algorithm**:
1. A bucket holds tokens (up to BurstLimit)
2. Tokens are added at RateLimit per second
3. Each request consumes one token
4. When bucket is empty, requests get HTTP 429

```
Bucket: [████████████████████] 200 tokens (burst)
        ↓ Request arrives
Bucket: [███████████████████ ] 199 tokens
        ↓ 100 requests/sec refill
```

### 5.3 Test Rate Limiting (LocalStack Pro Only)

If you have LocalStack Pro, you can test rate limiting:
```bash
# Delete existing stage
awslocal apigatewayv2 delete-stage \
    --api-id $DORNACH_API_ID \
    --stage-name prod

# Create with throttling
awslocal apigatewayv2 create-stage \
    --api-id $DORNACH_API_ID \
    --stage-name prod \
    --default-route-settings "ThrottlingBurstLimit=10,ThrottlingRateLimit=5"

# Test with many requests
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" $GATEWAY/users
done | sort | uniq -c
```

**Expected result:** After exceeding the rate limit, you'll see HTTP **429 Too Many Requests**.

---

## Challenge: Add Health Route (Optional)

### Context

In production, load balancers and monitoring systems need to check if services are healthy. Instead of exposing each service's health endpoint directly, we can expose a single `/health` route through the gateway.

**Goal:** Add a `/health` route that returns the health status of one of our services.

### Steps

1. **Check that the environment variables are set:**
   ```bash
   # Linux/macOS
   echo $DORNACH_API_ID
   echo $GATEWAY

   # Windows PowerShell
   echo $env:DORNACH_API_ID
   echo $env:GATEWAY
   ```

   If the variables are empty or incorrect, set them:
   ```bash
   # Linux/macOS
   export DORNACH_API_ID=<your-api-id>
   export GATEWAY="http://localhost:4566/restapis/$DORNACH_API_ID/prod/_user_request_"

   # Windows PowerShell
   $env:DORNACH_API_ID="<your-api-id>"
   $env:GATEWAY="http://localhost:4566/restapis/$env:DORNACH_API_ID/prod/_user_request_"
   ```

2. **Create an integration for the health endpoint:**

   Unlike the other integrations that forward paths dynamically, this one points to a specific endpoint:
   ```bash
   HEALTH_INT=$(awslocal apigatewayv2 create-integration \
       --api-id $DORNACH_API_ID \
       --integration-type HTTP_PROXY \
       --integration-uri http://host.docker.internal:8081/actuator/health \
       --integration-method GET \
       --query 'IntegrationId' --output text)

   echo "Health integration created: $HEALTH_INT"
   ```

3. **Create the route:**

   Note that we use `GET /health` instead of `ANY /health` since health checks are read-only:
   ```bash
   awslocal apigatewayv2 create-route \
       --api-id $DORNACH_API_ID \
       --route-key "GET /health" \
       --target "integrations/$HEALTH_INT"
   ```

4. **Test the health route:**
   ```bash
   curl $GATEWAY/health
   ```

   **Expected response:**
   ```json
   {"status":"UP"}
   ```

### Going Further

**Question:** How would you aggregate health from all three services?

<details>
<summary>💡 Answer</summary>

The gateway can only route to one backend per route. To aggregate health from multiple services, you would need:

1. **Option A:** Create a dedicated health aggregator service that calls all services and combines results
2. **Option B:** Use Spring Boot's composite health indicators in one of the services
3. **Option C:** Create separate routes (`/health/users`, `/health/orders`, `/health/shipments`) and let the client aggregate

In production, tools like Kubernetes or AWS ELB handle health checks per service independently.

</details>

</details>

---

## Validation with Bruno

### Setup Bruno Environment

1. Open Bruno
2. Keep the **Direct** environment selected
3. The `api_id` is automatically updated by the setup script

> **Note:** The setup script updates `bruno/environments/Direct.bru` with the new API ID. The `gateway` variable uses interpolation (`{{base_url}}/restapis/{{api_id}}/...`) so it updates automatically.

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
- [ ] `$GATEWAY/users/{id}` returns a single user (path forwarding works)
- [ ] `$GATEWAY/orders` routes correctly to order-service
- [ ] `$GATEWAY/shipments` routes correctly to shipment-service
- [ ] Bruno "Step 4 - API Gateway" tests pass
- [ ] (Optional, LocalStack Pro) Rate limiting works (429 returned when limit exceeded)

---

## Summary

In this step, you learned:
- **API Gateway** provides a single entry point for multiple services
- **HTTP API v2** is lightweight and cost-effective
- **Path-based routing** directs requests to appropriate backends
- **Rate limiting** protects services from abuse (Token Bucket algorithm)
- **LocalStack** emulates AWS services locally for development

---

<details>
<summary><strong>Bruno Collection Reference - Step 4</strong></summary>

### Gateway Tests (Single Entry Point)

| # | Request | Method | URL | Description |
|---|---------|--------|-----|-------------|
| 1 | List Users via Gateway | GET | `/users` | Verify routing to user-service |
| 2 | Create User via Gateway | POST | `/users` | Test POST requests through gateway |
| 3 | List Orders via Gateway | GET | `/orders` | Verify routing to order-service |
| 4 | List Shipments via Gateway | GET | `/shipments` | Verify routing to shipment-service |
| 5 | Unknown Route - 404 | GET | `/unknown` | Verify unconfigured routes return 404 |

### Direct Service Tests (comparison)

Subfolders for each service with Health Check, OpenAPI Spec, and List endpoints.

**Key tests validated:**
- Path-based routing: `/users/*` → user-service, `/orders/*` → order-service
- Gateway transparency (same response as direct call)
- Unknown route handling (404)

**Prerequisites:**
1. LocalStack running: `docker-compose up -d localstack`
2. Gateway created: `./infra/setup-gateway.sh`
3. `api_id` variable configured in Bruno environment

**Environment variables used:**
- `api_id`: API Gateway ID (retrieved from setup-gateway.sh)
- `gateway`: Full gateway URL (computed with api_id)

</details>

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
