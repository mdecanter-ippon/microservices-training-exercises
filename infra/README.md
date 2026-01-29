# Infrastructure Setup

This directory contains scripts to configure the Dornach infrastructure with LocalStack.

## Prerequisites

### 1. AWS CLI

Install the AWS CLI (required for all platforms):

| Platform | Installation |
|----------|-------------|
| **Windows** | `winget install Amazon.AWSCLI` or download from https://aws.amazon.com/cli/ |
| **macOS** | `brew install awscli` |
| **Linux** | `pip install awscli` or use your package manager |

### 2. awslocal (Linux/macOS only)

For bash scripts on Linux/macOS:
```bash
pip install awscli-local
```

> **Note:** Windows users don't need `awslocal` - use the PowerShell script (`setup-gateway.ps1`) which uses `aws --endpoint-url` directly.

### 3. LocalStack Auth Token

Set your LocalStack auth token before running `docker-compose`:
```bash
# Linux/macOS
export LOCALSTACK_AUTH_TOKEN="your-token"

# Windows PowerShell
$env:LOCALSTACK_AUTH_TOKEN="your-token"
```

## Quick Start

### 1. Start LocalStack

```bash
# From project root
docker-compose up -d localstack

# Wait for LocalStack to be ready
curl http://localhost:4566/_localstack/health
```

### 2. Start All Services

```bash
# Terminal 1: user-service
mvn spring-boot:run -pl user-service

# Terminal 2: shipment-service
mvn spring-boot:run -pl shipment-service

# Terminal 3: order-service
mvn spring-boot:run -pl order-service
```

### 3. Setup API Gateway

```bash
cd infra
./setup-gateway.sh
```

This creates:
- HTTP API Gateway (v2)
- Path-based routing to services
- Rate limiting (100 req/s, burst 200)

**Example output:**
```
API Gateway setup complete!

Gateway URL: http://localhost:4566/restapis/abc123/prod/_user_request_

Test the gateway:
  curl http://localhost:4566/restapis/abc123/prod/_user_request_/users
```

### 4. Test the Gateway

```bash
# Save the API ID
export DORNACH_API_ID=abc123

# Run tests
./test-gateway.sh
```

## Architecture

```
                 ┌────────────────────┐
                 │   API Gateway      │
                 │   (LocalStack)     │
                 │   :4566            │
                 └─────────┬──────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
    /users/*         /shipments/*      /orders/*
          │                │                │
          ▼                ▼                ▼
    ┌─────────┐      ┌─────────┐      ┌─────────┐
    │  user   │      │shipment │      │  order  │
    │ service │      │ service │      │ service │
    │  :8081  │      │  :8082  │      │  :8083  │
    └─────────┘      └─────────┘      └─────────┘
```

## Routing Rules

| Gateway Path | Target Service | Notes |
|-------------|----------------|-------|
| `/users/*` | user-service:8081 | User management |
| `/shipments/*` | shipment-service:8082 | Shipment tracking |
| `/orders/*` | order-service:8083 | Order orchestration |

## Rate Limiting

- **Rate Limit:** 100 requests/second
- **Burst Limit:** 200 requests
- **Response:** HTTP 429 (Too Many Requests)

**Testing rate limits:**
```bash
# Send 105 requests quickly
for i in {1..105}; do
    curl -s -o /dev/null -w "%{http_code}\n" \
        http://localhost:4566/restapis/$DORNACH_API_ID/prod/_user_request_/users
done
```

## Scripts

### `setup-gateway.sh` (Linux/macOS/Git Bash)
Creates the API Gateway with all routes and throttling.

**Usage:**
```bash
./setup-gateway.sh
```

### `setup-gateway.ps1` (Windows PowerShell)
Same as above, but for Windows. Uses `aws --endpoint-url` directly instead of `awslocal`.

**Usage:**
```powershell
.\setup-gateway.ps1
```

### `test-gateway.sh`
Tests routing and rate limiting.

**Usage:**
```bash
export DORNACH_API_ID=<your-api-id>
./test-gateway.sh
```

### `cleanup-gateway.sh`
Deletes the API Gateway.

**Usage:**
```bash
export DORNACH_API_ID=<your-api-id>
./cleanup-gateway.sh
```

### `setup-keycloak.sh`
Configures Keycloak realm, clients, and users.

**Usage:**
```bash
./setup-keycloak.sh
```

## Troubleshooting

### Gateway returns 404

**Problem:** Routes not configured properly.

**Solution:**
```bash
# List routes
awslocal apigatewayv2 get-routes --api-id $DORNACH_API_ID

# Recreate gateway
./cleanup-gateway.sh
./setup-gateway.sh
```

### Cannot connect to services

**Problem:** Services not running or wrong host.

**Solution:**
```bash
# Check services
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health

# For Docker, use host.docker.internal instead of localhost
```

### Rate limiting not working

**Problem:** Need more concurrent requests to hit limit.

**Solution:**
```bash
# Use apache bench for concurrent requests
ab -n 200 -c 50 http://localhost:4566/restapis/$DORNACH_API_ID/prod/_user_request_/users
```

## API Gateway v2 vs v1

**Why HTTP API (v2)?**
- 71% cheaper than REST API (v1)
- Lower latency (~10ms vs ~20ms)
- Simpler configuration
- Perfect for microservices routing

**When to use REST API (v1)?**
- Need WAF integration
- Need request/response transformations
- Complex authorization requirements

## Next Steps

1. **Step 5:** Add JWT validation at the gateway
2. **Step 6:** Configure service-to-service authentication
3. **Step 7:** Add distributed tracing

## References

- [LocalStack API Gateway](https://docs.localstack.cloud/user-guide/aws/apigateway/)
- [AWS API Gateway v2](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api.html)
- [Throttling Settings](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-throttling.html)
