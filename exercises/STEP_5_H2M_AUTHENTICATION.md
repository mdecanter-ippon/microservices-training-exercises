# Step 5: H2M Authentication (Human-to-Machine)

---

## Recap: Step 4

In Step 4, you configured an **API Gateway** with LocalStack:
- **HTTP API v2** created with `awslocal` CLI
- **Path-based routing** to user-service, order-service, and shipment-service
- **Single entry point** at `localhost:4566` instead of multiple ports
- **Rate limiting** with Token Bucket algorithm (burst + rate limits)
- **`setup-gateway.sh`** script for reproducible infrastructure

---

## Objectives

By the end of this exercise, you will:
- Understand OAuth2 and OpenID Connect (OIDC) fundamentals
- Configure Keycloak as an Identity Provider
- Secure Spring Boot services with JWT tokens
- Obtain tokens and call protected endpoints
- Decode and inspect JWT tokens

---

## Prerequisites

- Step 4 completed (API Gateway working)
- **Bruno** installed (https://www.usebruno.com/downloads)
- Docker and Docker Compose installed
- Basic understanding of authentication vs authorization

---

## Context

Currently, your services have no authentication. Anyone can call any endpoint. You need to:
1. Verify user identity (authentication)
2. Know who is calling (for audit, personalization)
3. Protect sensitive endpoints

**Solution:** OAuth2 + OpenID Connect with Keycloak.

```
                            ┌──────────────┐
                            │   Keycloak   │
                            │  (Identity)  │
                            └──────┬───────┘
                                   │ JWT Token
    ┌────────┐   1. Login          │
    │  User  │ ─────────────────→  │
    └────┬───┘                     │
         │   2. JWT Token          │
         │ ←───────────────────────┘
         │
         │   3. Request + JWT
         ▼
    ┌────────────┐
    │  Service   │  4. Validate JWT
    └────────────┘     (using Keycloak public keys)
```

---

## Exercise 1: Start and Configure Keycloak

### 1.1 Start Keycloak

```bash
docker-compose up -d keycloak
```

Wait for Keycloak to be ready:
```bash
# Check if Keycloak is responding
curl -sf http://localhost:8080/realms/master && echo "Keycloak is ready!"
```

### 1.2 Run the Setup Script

```bash
cd infra
./setup-keycloak.sh
```

This script creates:
- Realm: `dornach`
- Client: `dornach-web` (public client for testing)
- Users: `alice` (user role), `bob` (admin role)

### 1.3 Explore Keycloak Admin Console (Optional)

Open http://localhost:8080/admin and login with `admin/admin`.

Navigate to:
- **Realm:** Select "dornach" from the dropdown
- **Users:** See alice and bob
- **Clients:** See dornach-web
- **Realm Roles:** See user and admin

---

## Exercise 2: Obtain a JWT Token

### 2.1 Get Token with cURL

```bash
# Get token for Alice (user role)
curl -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password'
```

You'll receive a JSON response with `access_token`, `expires_in`, etc.

### 2.2 Extract and Store the Token

```bash
# Store token in a variable
TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password' | jq -r '.access_token')

echo $TOKEN
```

### 2.3 Decode the JWT

A JWT has 3 parts: `header.payload.signature`

```bash
# Decode the payload (middle part)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq
```

You should see claims like:
- `iss` - Issuer (Keycloak URL)
- `sub` - Subject (User ID)
- `exp` - Expiration timestamp
- `preferred_username` - Username
- `realm_access.roles` - User's roles

**Alternative:** Paste your token at https://jwt.io to decode it visually.

---

## Exercise 3: Configure Spring Security

**Files to modify:**
- `user-service/src/main/java/com/dornach/user/config/SecurityConfig.java`
- `user-service/src/main/resources/application.yaml`

### 3.1 Add application.yaml Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/dornach
```

<details>
<summary>💡 Explanation</summary>

- `issuer-uri` tells Spring where to validate tokens
- Spring automatically downloads Keycloak's public keys
- It verifies the JWT signature and expiration

</details>

### 3.2 Create SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // TODO: Allow actuator endpoints without authentication
                // TODO: Require authentication for all other requests
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

<details>
<summary>💡 Hint</summary>

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/**").permitAll()
    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
    .anyRequest().authenticated()
)
```

</details>

### 3.3 Restart the Service

```bash
# Stop and restart user-service
cd user-service && mvn spring-boot:run
```

---

## Exercise 4: Test Protected Endpoints

### 4.1 Test Without Token

```bash
curl http://localhost:8081/users
# Should return 401 Unauthorized
```

### 4.2 Test With Token

```bash
# Get a fresh token
TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password' | jq -r '.access_token')

# Call with token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users
# Should return 200 OK with users list
```

### 4.3 Test with Invalid Token

```bash
curl -H "Authorization: Bearer invalid-token" http://localhost:8081/users
# Should return 401 Unauthorized
```

---

## Exercise 5: Secure All Services

Apply the same configuration to:
- `order-service`
- `shipment-service`

### 5.1 Add Dependencies (if not present)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### 5.2 Copy Configuration

Copy the `SecurityConfig.java` and update `application.yaml` in each service.

### 5.3 Test All Services

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \
  -d 'client_id=dornach-web' \
  -d 'username=alice' \
  -d 'password=alice123' \
  -d 'grant_type=password' | jq -r '.access_token')

# Test each service
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/shipments
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/orders
```

---

## Exercise 6: Access User Information in Code

**File:** `user-service/src/main/java/com/dornach/user/controller/UserController.java`

### 6.1 Get Current User

You can access the authenticated user's information:

```java
@GetMapping("/me")
public Map<String, Object> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "username", jwt.getClaimAsString("preferred_username"),
        "email", jwt.getClaimAsString("email"),
        "roles", jwt.getClaimAsStringList("realm_access.roles")
    );
}
```

### 6.2 Test the Endpoint

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users/me
```

---

## Challenge: Implement Role-Based Access (Optional)

Add an endpoint that only admins can access:

```java
@GetMapping("/admin/stats")
@PreAuthorize("hasRole('admin')")
public Map<String, Object> getAdminStats() {
    return Map.of("totalUsers", userRepository.count());
}
```

Don't forget to enable method security:
```java
@EnableMethodSecurity
public class SecurityConfig {
```

Test with alice (user) vs bob (admin).

---

## Validation with Bruno

### Setup

1. Open Bruno
2. Select the **Direct** environment
3. Navigate to **Step 5 - H2M Authentication**

### Run the Tests

1. **Get Token - Alice (user)** - Click Send, token is saved automatically
2. **Get Token - Bob (admin)** - Click Send, token is saved automatically
3. **List Users (with Alice token)** - Should return 200
4. **List Users (no token)** - Should return 401

The tests verify:
- Token generation works
- Protected endpoints require authentication
- Valid tokens grant access

---

## Validation Checklist

Before moving to Step 6, verify:

- [ ] Keycloak is running and realm "dornach" exists
- [ ] Can obtain a token for alice and bob
- [ ] Token contains expected claims (iss, sub, roles)
- [ ] `GET /users` without token returns 401
- [ ] `GET /users` with valid token returns 200
- [ ] All three services (user, order, shipment) are secured
- [ ] Bruno "Step 5 - H2M Authentication" tests pass

---

## Summary

In this step, you learned:
- **OAuth2/OIDC** provides standardized authentication
- **Keycloak** acts as the Identity Provider
- **JWT tokens** are self-contained, stateless credentials
- **Spring Security** validates tokens using Keycloak's public keys
- **Resource Owner Password** flow is simple for testing (production uses Authorization Code + PKCE)

---

## Common Issues

### "The iss claim is not valid"

The token's issuer doesn't match the configured issuer. Make sure:
- Token obtained from same URL as configured in `issuer-uri`
- No mismatch between `localhost` and `keycloak` hostnames

### "Unable to resolve the Configuration"

Keycloak is not accessible from the service. Check:
- Keycloak is running
- URL is correct
- Network connectivity

---

## Before Moving On

Make sure you're ready for Step 6:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 5"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-5-complete
```

**Next:** [Step 6 - M2M Authentication](./STEP_6_M2M_AUTHENTICATION.md)
