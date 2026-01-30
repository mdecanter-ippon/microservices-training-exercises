# Step 6: M2M Authentication (Machine-to-Machine)

---

## Recap: Step 5

In Step 5, you implemented **H2M (Human-to-Machine) authentication**:
- **Keycloak** configured as Identity Provider with realm "dornach"
- **JWT tokens** obtained via Resource Owner Password flow
- **SecurityConfig** with `oauth2ResourceServer` to validate tokens
- **Protected endpoints** requiring `Authorization: Bearer <token>` header
- **@AuthenticationPrincipal Jwt** to access user claims in controllers
- Users `alice` (user role) and `bob` (admin role) for testing

---

## Objectives

By the end of this exercise, you will:
- Understand the difference between H2M and M2M authentication
- Configure OAuth2 Client Credentials flow
- Create a confidential client in Keycloak
- Implement automatic token management for service-to-service calls
- Apply role-based access control (RBAC) to protect endpoints

---

## Prerequisites

- Step 5 completed (H2M authentication working)
- **Bruno** installed (https://www.usebruno.com/downloads)
- Keycloak running with realm "dornach"
- All services running

---

## Context

In Step 5, we secured endpoints for human users. But what about service-to-service calls?

**Problem:**
```
User (Alice) --> order-service --> shipment-service
    [OK]            [???]            [DENIED]
  (has token)    (needs token)   (requires auth!)
```

When `order-service` calls `shipment-service`:
- It can't use Alice's token (different context)
- Without a token, the request is rejected (401)

**Solution: M2M Authentication**
- Services get their own credentials (client_id + secret)
- They obtain tokens using Client Credentials flow
- Each service authenticates as itself, not on behalf of a user

---

## Exercise 1: Create M2M Client in Keycloak

### 1.1 Run the Setup Script

The setup script already creates the M2M client. Re-run it to ensure everything is configured:

**Linux/macOS/Git Bash:**
```bash
cd infra
./setup-keycloak.sh
```

**Windows PowerShell:**
```powershell
cd infra\windows
.\setup-keycloak.ps1
```

Look for these lines in the output:
```
✓ M2M Client: order-service-client
✓ Service Account Role: service-caller
```

### 1.2 Verify in Keycloak Console (Optional)

Open http://localhost:8080/admin (admin/admin):
1. Select realm "dornach"
2. Go to **Clients** → **order-service-client**
3. Verify:
   - **Client authentication:** ON (confidential client)
   - **Service accounts roles:** ENABLED

### 1.3 Understand the Difference

| | dornach-web (H2M) | order-service-client (M2M) |
|---|---|---|
| **Purpose** | Human login | Service-to-service |
| **Client Type** | Public | Confidential |
| **Secret** | None | Required |
| **Grant Type** | Password | Client Credentials |

---

## Exercise 2: Obtain M2M Token Manually

### 2.1 Get Token with cURL

```bash
# Get M2M token
M2M_TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=order-service-client' \
  -d 'client_secret=order-service-secret' \
  -d 'grant_type=client_credentials' | jq -r '.access_token')

echo $M2M_TOKEN
```

Note: No `username` or `password` - the service authenticates with client credentials only.

### 2.2 Decode and Compare Tokens

```bash
# Decode M2M token
echo $M2M_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq

# Compare with H2M token
ALICE_TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password' | jq -r '.access_token')

echo $ALICE_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq
```

**Key differences:**
- M2M token has no `preferred_username` or `email`
- M2M token has `service-caller` role
- Alice's token has `user` role

---

## Exercise 3: Configure OAuth2 Client in order-service

**Files to modify:**
- `order-service/src/main/resources/application.yaml`
- `order-service/src/main/java/com/dornach/order/config/RestClientConfig.java`

### 3.1 Add OAuth2 Client Configuration

Add the following under the existing `spring.security.oauth2` section in `application.yaml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          shipment-service:
            provider: keycloak
            client-id: order-service-client
            client-secret: ${ORDER_SERVICE_CLIENT_SECRET:order-service-secret}
            authorization-grant-type: client_credentials
        provider:
          keycloak:
            token-uri: http://localhost:8080/realms/dornach/protocol/openid-connect/token
```

<details>
<summary>💡 Explanation</summary>

- `registration.shipment-service` - Name for this client registration (used in code)
- `client-id/secret` - Credentials for M2M authentication
- `authorization-grant-type` - Uses Client Credentials flow
- `provider.keycloak.token-uri` - Keycloak endpoint for tokens

</details>

### 3.2 Add OAuth2 Client Manager Bean

First, add these imports to `RestClientConfig.java`:

```java
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
```

Then add the following bean:

```java
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientRepository authorizedClientRepository) {

    OAuth2AuthorizedClientProvider authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build();

    DefaultOAuth2AuthorizedClientManager authorizedClientManager =
            new DefaultOAuth2AuthorizedClientManager(
                    clientRegistrationRepository,
                    authorizedClientRepository);

    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
}
```

### 3.3 Modify shipmentRestClient to Use OAuth2 Interceptor

Modify the existing `shipmentRestClient` bean to inject the `OAuth2AuthorizedClientManager` and add a custom interceptor that automatically obtains and attaches M2M tokens:

```java
@Bean
public RestClient shipmentRestClient(
        RestClient.Builder builder,
        OAuth2AuthorizedClientManager authorizedClientManager) {

    return builder
            .baseUrl(shipmentServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestInterceptor((request, body, execution) -> {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId("shipment-service")
                        .principal("order-service")
                        .build();

                OAuth2AuthorizedClient authorizedClient =
                        authorizedClientManager.authorize(authorizeRequest);

                if (authorizedClient != null) {
                    String token = authorizedClient.getAccessToken().getTokenValue();
                    request.getHeaders().setBearerAuth(token);
                }

                return execution.execute(request, body);
            })
            .build();
}
```

<details>
<summary>💡 How the interceptor works</summary>

The lambda interceptor:
1. Creates an `OAuth2AuthorizeRequest` with the client registration ID "shipment-service"
2. Uses the `OAuth2AuthorizedClientManager` to obtain or refresh the token
3. Adds the token to the `Authorization: Bearer` header
4. Executes the original request

The manager handles token caching and automatic refresh when the token expires.

</details>

The interceptor automatically:
1. Gets a token from Keycloak (if not cached)
2. Adds `Authorization: Bearer <token>` to every request
3. Refreshes the token when it expires

### 3.4 Apply the Same Pattern to userRestClient

Since user-service also requires authentication (from Step 5), apply the same OAuth2 interceptor pattern to `userRestClient`:

```java
@Bean
public RestClient userRestClient(
        RestClient.Builder builder,
        OAuth2AuthorizedClientManager authorizedClientManager) {

    return builder
            .baseUrl(userServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestInterceptor((request, body, execution) -> {
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId("shipment-service")
                        .principal("order-service")
                        .build();

                OAuth2AuthorizedClient authorizedClient =
                        authorizedClientManager.authorize(authorizeRequest);

                if (authorizedClient != null) {
                    String token = authorizedClient.getAccessToken().getTokenValue();
                    request.getHeaders().setBearerAuth(token);
                }

                return execution.execute(request, body);
            })
            .build();
}
```

> **Note:** We reuse the same client registration "shipment-service" since the M2M token works for all authenticated services.

---

## Exercise 4: Protect shipment-service with RBAC

**File to modify:** `shipment-service/src/main/java/com/dornach/shipment/config/SecurityConfig.java`

### 4.1 Enable Method Security

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    // ...
}
```

### 4.2 Add Role Converter

Keycloak stores roles in `realm_access.roles`. Spring Security needs a converter:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
    return converter;
}

static class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || realmAccess.get("roles") == null) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
```

### 4.3 Wire the Converter to SecurityFilterChain

**Important:** The converter must be explicitly wired to the JWT configuration:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
}
```

<details>
<summary>💡 Why is this step necessary?</summary>

Creating the `JwtAuthenticationConverter` bean is not enough - Spring Security's OAuth2 resource server uses defaults unless you explicitly configure it. The `.jwt(jwt -> jwt.jwtAuthenticationConverter(...))` line tells Spring to use your custom converter instead of the default one.

Without this, roles from `realm_access.roles` won't be extracted and RBAC with `@PreAuthorize` won't work.

</details>

### 4.4 Protect Endpoint with @PreAuthorize

**File:** `shipment-service/src/main/java/com/dornach/shipment/controller/ShipmentController.java`

```java
@PostMapping
@Operation(summary = "Create a new shipment")
@ApiResponse(responseCode = "201", description = "Shipment created successfully")
@ApiResponse(responseCode = "400", description = "Validation error")
@ApiResponse(responseCode = "403", description = "Access denied - requires service-caller or admin role")
@PreAuthorize("hasRole('service-caller') or hasRole('admin')")
public ResponseEntity<ShipmentResponse> createShipment(
        @Valid @RequestBody CreateShipmentRequest request) {
    // ...
}
```

This allows:
- M2M calls (service-caller role) - ALLOWED
- Admin users (admin role) - ALLOWED
- Regular users (user role) - DENIED (403 Forbidden)

### 4.5 Handle AccessDeniedException in GlobalExceptionHandler

**File to modify:** `shipment-service/src/main/java/com/dornach/shipment/exception/GlobalExceptionHandler.java`

The default `@ExceptionHandler(Exception.class)` will catch `AccessDeniedException` and return 500 instead of 403. Add a specific handler:

```java
import org.springframework.security.access.AccessDeniedException;

// ... existing handlers ...

@ExceptionHandler(AccessDeniedException.class)
public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "You don't have permission to perform this action"
    );
    problem.setTitle("Access Denied");
    problem.setType(URI.create("https://api.dornach.com/errors/access-denied"));
    problem.setProperty("timestamp", Instant.now());

    return problem;
}
```

> **Important:** Place this handler BEFORE the generic `@ExceptionHandler(Exception.class)` so it catches security exceptions first.

---

## Exercise 5: Test M2M Authentication

### 5.1 Test with M2M Token (Should Work)

```bash
M2M_TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=order-service-client' \
  -d 'client_secret=order-service-secret' \
  -d 'grant_type=client_credentials' | jq -r '.access_token')

curl -X POST http://localhost:8082/shipments \
  -H "Authorization: Bearer $M2M_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "recipientName": "Test User",
    "recipientAddress": "123 Main St"
  }'

# Should return 201 Created
```

### 5.2 Test with Alice's Token (Should Fail)

```bash
ALICE_TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password' | jq -r '.access_token')

curl -X POST http://localhost:8082/shipments \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "550e8400-e29b-41d4-a716-446655440001",
    "recipientName": "Alice",
    "recipientAddress": "456 Other St"
  }'

# Should return 403 Forbidden (Alice has "user" role, not "service-caller")
```

### 5.3 Test Automatic Token via order-service

Create an order - order-service will automatically get M2M token to call shipment-service:

```bash
BOB_TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=bob' \
  -d 'password=bob123' \
  -d 'grant_type=password' | jq -r '.access_token')

curl -X POST http://localhost:8083/orders \
  -H "Authorization: Bearer $BOB_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 1,
    "totalPrice": 999.99,
    "shippingAddress": "123 Main St"
  }'
```

Check order-service logs to see automatic M2M token usage.

---

## Challenge: Create M2M Client for Another Service (Optional)

Create a new M2M client for `shipment-service` to call `user-service`:
1. Add `shipment-service-client` in Keycloak
2. Configure OAuth2 client in shipment-service
3. Add interceptor to userRestClient
4. Test the flow

---

## Validation with Bruno

### Run Step 6 Tests

1. Open Bruno
2. Select the **Direct** environment
3. Navigate to **Step 6 - M2M Authentication**

Run these requests:
1. **Get M2M Token** - Should return access_token
2. **Create Shipment (M2M token)** - Should return 201
3. **Create Shipment (Alice token - should fail)** - Should return 403

---

## Validation Checklist

Before moving to Step 7, verify:

- [ ] M2M client `order-service-client` exists in Keycloak
- [ ] Can obtain M2M token with client credentials
- [ ] M2M token contains `service-caller` role
- [ ] `POST /shipments` with M2M token returns 201
- [ ] `POST /shipments` with Alice's token returns 403
- [ ] order-service automatically gets M2M token for shipment calls
- [ ] Bruno "Step 6 - M2M Authentication" tests pass

---

## Summary

In this step, you learned:
- **Client Credentials flow** is for service-to-service authentication
- **Confidential clients** have a secret (unlike public clients)
- **OAuth2AuthorizedClientManager** handles token lifecycle automatically
- **OAuth2ClientHttpRequestInterceptor** adds tokens to requests
- **RBAC with @PreAuthorize** controls access based on roles

---

<details>
<summary><strong>Bruno Collection Reference - Step 6</strong></summary>

### Recommended Test Sequence

| # | Request | Method | URL | Description |
|---|---------|--------|-----|-------------|
| 1 | Get M2M Token | POST | `/realms/dornach/.../token` | Get M2M token via Client Credentials flow, saved to `m2m_token` |
| 2 | Create Shipment - M2M Token (201) | POST | `/shipments` | Create shipment with M2M token - succeeds (service-caller role) |
| 3 | Create Shipment - Alice Token (403) | POST | `/shipments` | Try with Alice's H2M token - fails (user role, not service-caller) |
| 4 | Create Shipment - Bob Token (201) | POST | `/shipments` | Create with Bob's token - succeeds (admin role allowed) |

**Key tests validated:**
- Client Credentials flow (no username/password, just client_id + secret)
- RBAC enforcement: M2M token with `service-caller` role → 201
- RBAC enforcement: H2M token with `user` role → 403 Forbidden
- Admin bypass: Bob's token with `admin` role → 201

**Prerequisites:**
1. Keycloak running with M2M client configured
2. Step 5 completed (need Alice's token for comparison test)

**Environment variables used:**
- `keycloak_url`: Keycloak URL (e.g., `http://localhost:8080`)
- `m2m_token`: M2M JWT token (auto-filled)
- `alice_token`: Alice's H2M token (from Step 5)
- `bob_token`: Bob's H2M token (from Step 5)

**M2M Client credentials:**
| Client ID | Client Secret | Role |
|-----------|---------------|------|
| order-service-client | order-service-secret | service-caller |

</details>

---

## Before Moving On

Make sure you're ready for Step 7:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 6"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-6-complete
```

**Next:** [Step 7 - Distributed Tracing](./STEP_7_DISTRIBUTED_TRACING.md)
