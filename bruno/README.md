# Bruno API Testing Collection

This Bruno collection contains API tests for the Dornach microservices platform.

## Prerequisites

1. **Install Bruno** (if not already installed):
   ```bash
   # macOS
   brew install bruno

   # Or download from https://www.usebruno.com/
   ```

2. **Start the infrastructure**:
   ```bash
   # Start all services
   docker-compose up -d

   # Setup API Gateway
   cd infra
   ./setup-gateway.sh
   ```

3. **Update the API ID**:
   - After running `setup-gateway.sh`, copy the API ID displayed
   - Open Bruno and edit the environment variable:
     - Go to **Environments** > **Local Direct**
     - Update `api_id` with your actual API ID (e.g., `eeb818fb`)

## Structure

```
bruno/
├── bruno.json                           # Collection config
├── README.md                            # This file
├── environments/
│   └── Local Direct.bru                 # Environment variables
├── Authentication/                      # Get JWT tokens
│   ├── Get Token - Alice (user).bru     # Get user token
│   └── Get Token - Bob (admin).bru      # Get admin token
├── Authenticated Requests/              # Use JWT tokens
│   ├── List Users (with Alice token).bru      # GET with user role
│   ├── Create User (with Bob token).bru       # POST with admin role
│   ├── List Shipments (with Alice token).bru  # GET shipments
│   └── List Orders (with Bob token).bru       # GET orders
├── Gateway Tests/                       # Via API Gateway
│   ├── User Service/
│   │   ├── Health Check.bru             # Public (200)
│   │   ├── OpenAPI Spec.bru             # Public (200)
│   │   └── List Users (requires JWT).bru # Protected (401)
│   ├── Shipment Service/
│   │   ├── Health Check.bru             # Public (200)
│   │   ├── OpenAPI Spec.bru             # Public (200)
│   │   └── List Shipments (requires JWT).bru # Protected (401)
│   └── Order Service/
│       ├── Health Check.bru             # Public (200)
│       ├── OpenAPI Spec.bru             # Public (200)
│       └── List Orders (requires JWT).bru # Protected (401)
└── Direct Access/                       # Direct to services
    ├── User Service/
    │   ├── Health Check.bru             # Public (200)
    │   ├── OpenAPI Spec.bru             # Public (200)
    │   └── List Users (requires JWT).bru # Protected (401)
    ├── Shipment Service/
    │   ├── Health Check.bru             # Public (200)
    │   ├── OpenAPI Spec.bru             # Public (200)
    │   └── List Shipments (requires JWT).bru # Protected (401)
    └── Order Service/
        ├── Health Check.bru             # Public (200)
        ├── OpenAPI Spec.bru             # Public (200)
        └── List Orders (requires JWT).bru # Protected (401)
```

## Testing Approaches

### Authentication (Step 5)
Get JWT tokens from Keycloak to test protected endpoints.

**How to use:**
1. Run "Get Token - Alice (user)" or "Get Token - Bob (admin)"
2. Token is automatically saved to environment (`alice_token` or `bob_token`)
3. Use authenticated requests to test with valid JWTs

**Available users:**
- **Alice** (alice123) - Role: user
- **Bob** (bob123) - Roles: user, admin

### Authenticated Requests
Test API endpoints with JWT authentication.

**Prerequisites:**
- Keycloak must be running and configured
- Obtain tokens from the Authentication folder first

**Examples:**
- List/Create Users with Alice or Bob token
- List Shipments with authentication
- List Orders with authentication

### Gateway Tests
Tests requests going through the **API Gateway** (LocalStack).
- URL format: `http://localhost:4566/restapis/{apiId}/prod/_user_request_/{service}`
- Validates that routing works correctly
- Simulates production traffic flow

### Direct Access
Tests requests going **directly to services** (bypassing gateway).
- URL format: `http://localhost:8081/...` (user), `8082` (shipment), `8083` (order)
- Useful for debugging service-level issues
- Faster response times (no gateway overhead)

**Use all approaches to:**
- Test authentication flows (Step 5)
- Verify gateway routing (Step 4)
- Debug service-level issues (Direct Access)
- Compare authenticated vs unauthenticated requests

## How to Use

1. **Open Bruno** and select **Open Collection**
2. Navigate to this `bruno/` directory
3. Select the **Local Direct** environment (top-right dropdown)
4. Run the requests:
   - Click on any request to see details
   - Click **Send** to execute
   - View response in the **Response** tab
   - View test results in the **Tests** tab

## Running All Tests

To run all tests in a folder:
1. Right-click on **Gateway Tests** or **Direct Access** folder
2. Select **Run Collection**
3. Bruno will execute all requests and show test results

### Comparing Gateway vs Direct
Run both collections and compare:
- Same responses prove gateway routes correctly
- Compare response times (gateway adds minimal latency)
- Both should show identical 401 errors for protected endpoints

## Expected Results

### Public Endpoints (Should Succeed)
- **Health Checks**: All return `{"status":"UP"}` with HTTP 200
- **OpenAPI Specs**: All return valid OpenAPI 3.1 JSON with HTTP 200

### Protected Endpoints (Should Return 401)
- **List Users/Shipments/Orders**: All return HTTP 401 with `WWW-Authenticate: Bearer`
- This is **expected behavior** - proves that:
  1. Gateway routes correctly to the service
  2. Spring Security is active and protecting endpoints

## Next Steps

After Step 5 (Keycloak), you'll be able to:
1. Obtain JWT tokens from Keycloak
2. Add them to requests using Bearer authentication
3. Test the protected endpoints with valid authentication

## Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `base_url` | LocalStack endpoint | `http://localhost:4566` |
| `api_id` | Your API Gateway ID | `eeb818fb` (update this!) |
| `gateway` | Full gateway URL | `{{base_url}}/restapis/{{api_id}}/prod/_user_request_` |
| `user_service_url` | Direct user service URL | `http://localhost:8081` |
| `shipment_service_url` | Direct shipment service URL | `http://localhost:8082` |
| `order_service_url` | Direct order service URL | `http://localhost:8083` |
| `keycloak_url` | Keycloak server URL | `http://localhost:8080` |
| `alice_token` | JWT token for Alice (auto-filled) | Obtained from Authentication requests |
| `bob_token` | JWT token for Bob (auto-filled) | Obtained from Authentication requests |

## Troubleshooting

### Variables not recognized (ENOTFOUND {{variable_name}})
This means Bruno is not substituting the variables correctly.

**Solution:**
- Make sure you've selected the **Local Direct** environment (dropdown in top-right)
- Verify variable names use underscores: `api_id`, `user_service_url`, etc.
- If needed, close and reopen Bruno to reload the environment

### All requests fail with connection errors
- Check that services are running: `docker-compose ps`
- Check that LocalStack is healthy: `curl http://localhost:4566/_localstack/health`

### Wrong API ID error
- Update the `api_id` variable in the environment with your actual API Gateway ID
- Get it by running: `awslocal apigatewayv2 get-apis`

### 404 Not Found on all endpoints
- Verify the gateway was set up: `cd infra && ./setup-gateway.sh`
- Check routes: `awslocal apigatewayv2 get-routes --api-id $DORNACH_API_ID`
