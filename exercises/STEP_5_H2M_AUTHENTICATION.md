# Step 5: H2M Authentication (Human-to-Machine)

---

## Recap: Step 4

In Step 4, you configured an **API Gateway** with LocalStack:
- **HTTP API v2** created with `awslocal` CLI
- **Path-based routing** to user-service, order-service, and shipment-service
- **Single entry point** at `localhost:4566` instead of multiple ports
- **`setup-gateway.sh`** (or `windows/setup-gateway.ps1`) script for reproducible infrastructure

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
- **jq** installed (for JSON parsing in terminal)

---

## Context

Currently, your services have no authentication. Anyone can call any endpoint. You need to:
1. Verify user identity (authentication)
2. Know who is calling (for audit, personalization)
3. Protect sensitive endpoints

**Solution:** OAuth2 + OpenID Connect with Keycloak.

### Architecture

```
                            ┌──────────────┐
                            │   Keycloak   │
                            │   (Docker)   │
                            │  :8080       │
                            └──────┬───────┘
                                   │
    ┌────────┐   1. Login          │
    │  User  │ ─────────────────→  │
    └────┬───┘                     │
         │   2. JWT Token          │
         │ ←───────────────────────┘
         │
         │   3. Request + JWT
         ▼
    ┌────────────┐
    │  Service   │  4. Validate JWT signature
    │ (Terminal) │     using Keycloak public keys
    │  :8081     │
    └────────────┘
```

> **Note:** Services run locally in terminals (not Docker containers) to simplify the setup. Keycloak runs in Docker on port 8080.

---

## Understanding OAuth2 and OIDC

### OAuth2 vs OpenID Connect

| Aspect | OAuth2 | OIDC (OpenID Connect) |
|--------|--------|----------------------|
| **Purpose** | Authorization (delegation) | Authentication (identity) |
| **Token** | Access Token | ID Token + Access Token |
| **Use Case** | "Access my Google Photos" | "Login with Google" |

**Key insight:** OIDC = OAuth2 + identity layer. We use OIDC because we need to know *who* the user is.

### Grant Types (How to get a token)

| Grant Type | Use Case |
|------------|----------|
| **Authorization Code + PKCE** | Web apps, Mobile apps (production) |
| **Client Credentials** | Machine-to-Machine (Step 6) |
| **Password (ROPC)** | Testing, CLI tools |

In this exercise, we use **Password flow** because it's simple for testing (username/password → token). In production, you'd use Authorization Code + PKCE.

### Deep Dive: Authorization Code + PKCE (Production Flow)

While we use Password flow for simplicity in this training, **Authorization Code + PKCE** is the recommended flow for production applications. Here's why and how it works.

#### Why Not Password Flow in Production?

| Aspect | Password Flow | Authorization Code + PKCE |
|--------|---------------|---------------------------|
| **Credentials** | App sees user password | App never sees password |
| **Security** | Vulnerable to credential theft | Password only enters IdP |
| **MFA Support** | Difficult to implement | Native support |
| **SSO** | Not possible | Full support |
| **OAuth 2.1** | Deprecated | Recommended |

**Key risk:** In Password flow, the application handles user credentials directly. If the app is compromised, attackers get passwords. With Authorization Code + PKCE, credentials never leave the Identity Provider.

#### How Authorization Code + PKCE Works

```
┌──────────┐                              ┌──────────────┐
│  User    │                              │   Keycloak   │
│ (Browser)│                              │     (IdP)    │
└────┬─────┘                              └──────┬───────┘
     │                                           │
     │  1. Click "Login"                         │
     │ ──────────────────────────────────────→   │
     │    (redirect to Keycloak login page)      │
     │    + code_challenge (hashed random)       │
     │                                           │
     │  2. User enters credentials               │
     │    (directly in Keycloak, not the app)    │
     │                                           │
     │  3. Redirect back with authorization_code │
     │ ←──────────────────────────────────────   │
     │                                           │
     │                    ┌──────────────┐       │
     │                    │  Backend     │       │
     │                    │  (optional)  │       │
     │                    └──────┬───────┘       │
     │                           │               │
     │  4. Exchange code for tokens              │
     │    + code_verifier (original random)      │
     │                    │ ─────────────────→   │
     │                    │                      │
     │  5. Access Token + ID Token               │
     │                    │ ←─────────────────   │
     │                                           │
```

**PKCE (Proof Key for Code Exchange):**
1. App generates a random `code_verifier`
2. App creates `code_challenge = SHA256(code_verifier)`
3. App sends `code_challenge` with the authorization request
4. App sends `code_verifier` when exchanging the code
5. IdP verifies: `SHA256(code_verifier) == code_challenge`

This prevents attackers from using stolen authorization codes.

#### Production Best Practices

| Practice | Reason |
|----------|--------|
| **Use Authorization Code + PKCE** | Never expose credentials to your app |
| **Store tokens securely** | HttpOnly cookies or secure storage |
| **Use short-lived access tokens** | Limit damage if token is stolen (5-15 min) |
| **Implement refresh tokens** | Get new access tokens without re-login |
| **Validate tokens server-side** | Never trust client-side validation alone |
| **Use HTTPS everywhere** | Prevent token interception |

#### Why We Use Password Flow Here

For this training, Password flow offers significant advantages:
- **No browser redirects** - Easy to test with cURL
- **No frontend needed** - Focus on backend security
- **Quick iteration** - Get tokens in one command
- **Clear demonstration** - See the full request/response

The security concepts (JWT validation, claims, roles) are identical regardless of the flow used to obtain the token.

---

## Exercise 1: Start and Configure Keycloak

### 1.1 Start Keycloak

```bash
docker-compose up -d keycloak
```

Wait for Keycloak to be ready (can take 20-30 seconds):
```bash
# Check if Keycloak is responding
curl -sf http://localhost:8080/realms/master && echo "Keycloak is ready!"
```

### 1.2 Run the Setup Script

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

This script creates:
- **Realm:** `dornach` (isolated tenant)
- **Client:** `dornach-web` (public client for testing with Password flow)
- **Roles:** `user`, `admin`
- **Users:**
  | Username | Password | Role |
  |----------|----------|------|
  | alice | alice123 | user |
  | bob | bob123 | admin |

### 1.3 Explore Keycloak Admin Console (Optional)

Open http://localhost:8080/admin and login with `admin/admin`.

Navigate to:
- **Realm:** Select "dornach" from the dropdown (top-left)
- **Users:** See alice and bob
- **Clients:** See dornach-web
- **Realm Roles:** See user and admin

---

## Exercise 2: Obtain a JWT Token

### 2.1 Get Token with cURL

```bash
# Get token for Alice (user role)
curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" | jq
```

You'll receive a JSON response with:
- `access_token` - The JWT to use in API calls
- `expires_in` - Token lifetime in seconds
- `token_type` - Always "Bearer"

### 2.2 Extract and Store the Token

```bash
# Store token in a variable
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" | jq -r '.access_token')

echo $TOKEN
```

### 2.3 Decode the JWT

A JWT has 3 parts separated by dots: `header.payload.signature`

```bash
# Decode the payload (middle part)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq
```

**Key claims explained:**

| Claim | Description | Example |
|-------|-------------|---------|
| `iss` | Issuer (who created the token) | `http://localhost:8080/realms/dornach` |
| `sub` | Subject (unique user ID) | `4bf2e9d0-a835-4d5e-...` |
| `exp` | Expiration (Unix timestamp) | `1735567234` |
| `preferred_username` | Human-readable username | `alice` |
| `realm_access.roles` | User's roles | `["user"]` |

**Visual alternative:** Paste your token at https://jwt.io to decode it visually.

---

## Exercise 3: Configure Spring Security

**Files to modify:**
- `user-service/src/main/java/com/dornach/user/config/SecurityConfig.java`
- `user-service/src/main/resources/application.yaml`

### 3.1 Add Dependencies

In `user-service/pom.xml`, ensure this dependency is present:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### 3.2 Add application.yaml Configuration

Add to `user-service/src/main/resources/application.yaml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/dornach
```

<details>
<summary>💡 What does Spring do with this?</summary>

1. Downloads Keycloak's public keys from `{issuer-uri}/.well-known/openid-configuration`
2. Caches the keys (refreshes every 5 minutes)
3. For each request with `Authorization: Bearer <token>`:
   - Validates the signature using the public key
   - Checks the `iss` claim matches `issuer-uri`
   - Verifies the token is not expired (`exp` claim)
   - If all checks pass → request is authenticated

</details>

### 3.3 Create SecurityConfig.java

Create `user-service/src/main/java/com/dornach/user/config/SecurityConfig.java`:

```java
package com.dornach.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
                // TODO: Configure which endpoints require authentication
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

<details>
<summary>💡 Complete the TODO</summary>

```java
.authorizeHttpRequests(authz -> authz
    .requestMatchers("/actuator/**").permitAll()
    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
    .anyRequest().authenticated()
)
```

- `/actuator/**` - Health checks should be public (for monitoring)
- `/v3/api-docs/**`, `/swagger-ui/**` - API documentation should be accessible
- Everything else requires a valid JWT

</details>

### 3.4 Restart the Service

```bash
# Stop the service (Ctrl+C) and restart
cd user-service && mvn spring-boot:run
```

---

## Exercise 4: Test Protected Endpoints

### 4.1 Test Without Token

```bash
curl -i http://localhost:8081/users
```

**Expected:** `HTTP 401 Unauthorized`

### 4.2 Test With Token

```bash
# Get a fresh token
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" | jq -r '.access_token')

# Call with token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users
```

**Expected:** `HTTP 200 OK` with users list

### 4.3 Test with Invalid Token

```bash
curl -i -H "Authorization: Bearer invalid-token" http://localhost:8081/users
```

**Expected:** `HTTP 401 Unauthorized`

### 4.4 Verify Actuator is Still Public

```bash
curl http://localhost:8081/actuator/health
```

**Expected:** `HTTP 200 OK` (no token required)

---

## Exercise 5: Secure All Services

Apply the same configuration to `order-service` and `shipment-service`.

### 5.1 For Each Service:

1. Add the `spring-boot-starter-oauth2-resource-server` dependency to `pom.xml`
2. Add the `issuer-uri` configuration to `application.yaml`
3. Create `SecurityConfig.java` (you can copy from user-service)
4. Restart the service

### 5.2 Test All Services

```bash
# Get token once
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" | jq -r '.access_token')

# Test each service
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/shipments
curl -H "Authorization: Bearer $TOKEN" http://localhost:8083/orders
```

All should return `HTTP 200 OK`.

---

## Exercise 6: Access User Information in Code

**File:** `user-service/src/main/java/com/dornach/user/controller/UserController.java`

### 6.1 Add a "Who Am I" Endpoint

Add this method to `UserController`:

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

Don't forget the import:
```java
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
```

### 6.2 Test the Endpoint

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/users/me
```

**Expected response:**
```json
{
  "username": "alice",
  "email": "alice@dornach.com",
  "roles": ["user", "default-roles-dornach", ...]
}
```

---

## Challenge: Implement Role-Based Access Control (Optional)

### Goal

Create an endpoint that only users with the `admin` role can access.

### Steps

1. **Enable method security** in `SecurityConfig.java`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Add this annotation
public class SecurityConfig {
    // ...
}
```

2. **Add a role converter** (Keycloak stores roles in a nested structure):

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return converter;
}
```

3. **Create an admin-only endpoint** in `UserController`:

```java
@GetMapping("/admin/stats")
@PreAuthorize("hasRole('admin')")
public Map<String, Object> getAdminStats() {
    return Map.of(
        "totalUsers", userRepository.count(),
        "message", "This endpoint is admin-only"
    );
}
```

4. **Test with different users:**

```bash
# Alice (user role) - should get 403 Forbidden
TOKEN_ALICE=$(curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" -d "username=alice" -d "password=alice123" -d "grant_type=password" | jq -r '.access_token')

curl -i -H "Authorization: Bearer $TOKEN_ALICE" http://localhost:8081/users/admin/stats

# Bob (admin role) - should get 200 OK
TOKEN_BOB=$(curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" -d "username=bob" -d "password=bob123" -d "grant_type=password" | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN_BOB" http://localhost:8081/users/admin/stats
```

---

## Bonus: Custom Protocol Mappers

Protocol Mappers allow you to add custom claims to JWT tokens. This is useful for including business-specific data like `department`, `tenant_id`, or `employee_number`.

### Why Custom Claims?

| Use Case | Custom Claim | Benefit |
|----------|--------------|---------|
| Multi-tenancy | `tenant_id` | Route requests to correct database |
| Department-based access | `department` | Fine-grained authorization |
| Audit logging | `employee_id` | Track who performed actions |
| Feature flags | `subscription_tier` | Enable/disable features per user |

### Adding a Custom Claim via Keycloak Admin

1. Open **Keycloak Admin Console** → http://localhost:8080/admin
2. Select **dornach** realm
3. Go to **Clients** → **dornach-web** → **Client scopes** tab
4. Click **dornach-web-dedicated** → **Add mapper** → **By configuration**
5. Select **User Attribute**
6. Configure:

| Field | Value |
|-------|-------|
| Name | department |
| User Attribute | department |
| Token Claim Name | department |
| Claim JSON Type | String |
| Add to ID token | ON |
| Add to access token | ON |

7. Click **Save**

### Setting the User Attribute

1. Go to **Users** → **alice** → **Attributes** tab
2. Add attribute:
   - Key: `department`
   - Value: `engineering`
3. Click **Save**

### Verify the Custom Claim

```bash
# Get a new token for Alice
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/dornach/protocol/openid-connect/token" \
  -d "client_id=dornach-web" \
  -d "username=alice" \
  -d "password=alice123" \
  -d "grant_type=password" | jq -r '.access_token')

# Decode and see the custom claim
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq '{
  username: .preferred_username,
  department: .department
}'
```

**Expected output:**
```json
{
  "username": "alice",
  "department": "engineering"
}
```

### Using Custom Claims in Spring

```java
@GetMapping("/me/department")
public Map<String, String> getDepartment(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "username", jwt.getClaimAsString("preferred_username"),
        "department", jwt.getClaimAsString("department")
    );
}
```

### Script Automation (Optional)

To add custom claims via script, add this to `setup-keycloak.sh`:

```bash
# Get client UUID
CLIENT_UUID=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach/clients?clientId=dornach-web" | jq -r '.[0].id')

# Get dedicated scope ID
SCOPE_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach/clients/${CLIENT_UUID}/dedicated-client-scopes" | jq -r '.[0].id')

# Create protocol mapper
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/client-scopes/${SCOPE_ID}/protocol-mappers/models" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "department",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-attribute-mapper",
        "config": {
            "user.attribute": "department",
            "claim.name": "department",
            "jsonType.label": "String",
            "id.token.claim": "true",
            "access.token.claim": "true"
        }
    }'
```

---

## Going Further: Identity Brokering (Theory)

Identity Brokering allows users to authenticate via external Identity Providers like Google, GitHub, or corporate SSO systems. Keycloak acts as a broker between your application and these external providers.

### How It Works

```
┌──────────┐                     ┌──────────────┐                    ┌──────────────┐
│   User   │                     │   Keycloak   │                    │   Google     │
│ (Browser)│                     │   (Broker)   │                    │   (IdP)      │
└────┬─────┘                     └──────┬───────┘                    └──────┬───────┘
     │                                  │                                   │
     │  1. Click "Login with Google"    │                                   │
     │ ───────────────────────────────→ │                                   │
     │                                  │                                   │
     │                                  │  2. Redirect to Google            │
     │                                  │ ────────────────────────────────→ │
     │                                  │                                   │
     │  3. User authenticates           │                                   │
     │ ←────────────────────────────────────────────────────────────────────│
     │                                  │                                   │
     │                                  │  4. Google returns user info      │
     │                                  │ ←──────────────────────────────── │
     │                                  │                                   │
     │                                  │  5. Keycloak creates/links user   │
     │                                  │     and issues its own JWT        │
     │                                  │                                   │
     │  6. JWT Token (Keycloak-issued)  │                                   │
     │ ←─────────────────────────────── │                                   │
     │                                  │                                   │
```

**Key insight:** Your application only trusts Keycloak tokens. It doesn't need to know about Google, GitHub, or any other provider. Keycloak handles all the complexity.

### Supported Identity Providers

| Category | Examples |
|----------|----------|
| **Social** | Google, Facebook, GitHub, Twitter, LinkedIn |
| **Enterprise** | SAML 2.0, Active Directory (via LDAP), Azure AD |
| **Standards-based** | Any OpenID Connect provider, Any SAML IdP |

### Benefits of Identity Brokering

| Benefit | Description |
|---------|-------------|
| **Single integration point** | Your app only integrates with Keycloak, not each IdP |
| **User account linking** | Same user can login via Google OR username/password |
| **Attribute mapping** | Normalize claims from different providers |
| **First login flow** | Collect additional info on first social login |
| **Centralized policy** | Apply MFA, session limits across all login methods |

### User Account Linking

When a user first logs in with Google:

1. Keycloak checks if email `alice@gmail.com` exists in realm
2. **If exists:** Links Google identity to existing account
3. **If not:** Creates new user OR prompts to link to existing account

This allows users to have multiple login methods for the same account.

### When to Use Identity Brokering

| Scenario | Recommendation |
|----------|----------------|
| Consumer app (B2C) | ✅ Offer Google, GitHub, Apple login |
| Enterprise app (B2B) | ✅ Federate with customer's Azure AD/Okta |
| Internal app | ⚠️ Usually just corporate SSO via SAML/LDAP |
| API-only service | ❌ No user interaction, use M2M instead |

### Configuration Overview (Not Implemented in This Training)

Setting up Identity Brokering requires:

1. **Register your app** with the external IdP (e.g., Google Cloud Console)
2. **Get credentials** (Client ID + Client Secret from Google)
3. **Configure Keycloak** → Identity Providers → Add Google
4. **Map attributes** (e.g., Google's `picture` → Keycloak's `avatar`)
5. **Configure first login flow** (what happens on first social login)

This is typically a one-time admin task, not something developers do daily.

### Token Comparison

Regardless of how the user authenticated, your app receives a standard Keycloak JWT:

```json
// User logged in with Google
{
  "iss": "http://localhost:8080/realms/dornach",
  "sub": "abc123...",
  "preferred_username": "alice",
  "email": "alice@gmail.com",
  "identity_provider": "google",        // Shows which IdP was used
  "realm_access": { "roles": ["user"] }
}

// User logged in with username/password
{
  "iss": "http://localhost:8080/realms/dornach",
  "sub": "abc123...",                    // Same user ID!
  "preferred_username": "alice",
  "email": "alice@gmail.com",
  // No identity_provider claim
  "realm_access": { "roles": ["user"] }
}
```

**Your application code doesn't change.** The same `@AuthenticationPrincipal Jwt jwt` works regardless of login method.

---

## Validation with Bruno

### Setup

1. Open Bruno
2. Select the **Direct** environment
3. Navigate to **Step 5 - H2M Authentication**

### Run the Tests

1. **Get Token - Alice** - Click Send, token is saved to `alice_token` variable
2. **Get Token - Bob** - Click Send, token is saved to `bob_token` variable
3. **List Users - With Token** - Should return 200
4. **List Users - No Token (401)** - Should return 401

<details>
<summary><strong>Bruno Collection Reference - Step 5</strong></summary>

### Recommended Test Sequence

| # | Request | Method | URL | Description |
|---|---------|--------|-----|-------------|
| 1 | Get Token - Alice | POST | `/realms/dornach/.../token` | Get JWT for Alice (role: user), saved to `alice_token` |
| 2 | Get Token - Bob | POST | `/realms/dornach/.../token` | Get JWT for Bob (role: admin), saved to `bob_token` |
| 3 | List Users - No Token (401) | GET | `/users` | Verify access denied without token (401 Unauthorized) |
| 4 | List Users - With Token | GET | `/users` | With Alice's token, access is allowed (200 OK) |
| 5 | Create User - With Token | POST | `/users` | Create user with Bob's token (admin) |

### Other Available Requests

- **List Orders (with Bob token)** - Test order-service access with JWT
- **List Shipments (with Alice token)** - Test shipment-service access with JWT

**Key tests validated:**
- Password Flow authentication (Resource Owner Password Credentials)
- Rejection of requests without token (401)
- Acceptance of requests with valid token (200)
- JWT token automatically saved for reuse

**Prerequisites:**
1. Keycloak running: `docker-compose up -d keycloak`
2. Realm configured: `./infra/setup-keycloak.sh`

**Environment variables used:**
- `keycloak_url`: Keycloak URL (e.g., `http://localhost:8080`)
- `alice_token`: Alice's JWT token (auto-filled)
- `bob_token`: Bob's JWT token (auto-filled)

**Preconfigured users:**
| User | Password | Role |
|------|----------|------|
| alice | alice123 | user |
| bob | bob123 | admin |

</details>

---

## Validation Checklist

Before moving to Step 6, verify:

- [ ] Keycloak is running (`curl http://localhost:8080/realms/dornach`)
- [ ] Can obtain a token for alice and bob
- [ ] Token contains expected claims (decode with `jwt.io`)
- [ ] `GET /users` without token returns 401
- [ ] `GET /users` with valid token returns 200
- [ ] `GET /actuator/health` works without token
- [ ] All three services (user, order, shipment) are secured
- [ ] Bruno "Step 5 - H2M Authentication" tests pass

---

## Troubleshooting

### "The iss claim is not valid"

**Cause:** The `iss` claim in the token doesn't match the configured `issuer-uri`.

**Solution:** Make sure you're using the same URL to get tokens and in the config:
```yaml
# application.yaml
issuer-uri: http://localhost:8080/realms/dornach
```

Get tokens from the same URL:
```bash
curl -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token ...
```

### "Unable to resolve the Configuration"

**Cause:** Spring can't reach Keycloak to download public keys.

**Check:**
1. Is Keycloak running? `curl http://localhost:8080/realms/dornach`
2. Is the URL correct in `application.yaml`?

### Token Expired

**Cause:** JWT tokens have a limited lifetime (default: 5 minutes in Keycloak).

**Solution:** Get a fresh token before testing.

### 403 Forbidden (not 401)

**Cause:** The token is valid but the user doesn't have the required role.

**Check:** Decode the token and verify `realm_access.roles` contains the expected role.

---

## Summary

In this step, you learned:
- **OAuth2/OIDC** provides standardized authentication
- **Keycloak** acts as the Identity Provider (issues and validates tokens)
- **JWT tokens** are self-contained credentials (no server-side sessions)
- **Spring Security** validates tokens using Keycloak's public keys
- **Password flow** is simple for testing (production uses Authorization Code + PKCE)

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
