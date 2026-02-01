# Bonus A: Integration Tests with Testcontainers

---

## Recap: Step 7

In Step 7, you implemented **distributed tracing**:
- **Zipkin** running as trace collector at `localhost:9411`
- **Micrometer Tracing** with OpenTelemetry bridge
- **Trace ID** shared across all services in a request
- **Log correlation** with `[serviceName,traceId,spanId]` pattern
- **Waterfall visualization** showing request flow across services
- **Error tracing** to identify which service caused failures

---

## Objectives

By the end of this exercise, you will:
- Understand why integration tests with real containers are valuable
- Configure Testcontainers for PostgreSQL and Keycloak
- Write tests that run against actual database and identity provider
- Use Spring Boot's `@ServiceConnection` for automatic configuration

---

## Prerequisites

- Step 7 completed
- **Docker Desktop** running (or Colima/Podman)
- Basic understanding of JUnit 5

---

## Context

Unit tests with mocks are fast but can hide bugs. Real integration tests catch issues that mocks miss:

| Test Type | Speed | Realism | Bugs Caught |
|-----------|-------|---------|-------------|
| Mocks | Fast | Low | Logic errors |
| Testcontainers | Slower | High | Integration issues |

**Testcontainers** spins up real Docker containers for your tests:
- PostgreSQL with actual SQL
- Keycloak with real JWT validation
- Automatic cleanup after tests

---

## Exercise 1: Setup Testcontainers with PostgreSQL

In this exercise, you'll configure Testcontainers and create a test class with a PostgreSQL container.

### 1.1 Add Dependencies to user-service

**File:** `user-service/pom.xml`

Add the following dependencies in the `<dependencies>` section:

```xml
<!-- Testcontainers Core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>

<!-- PostgreSQL Container -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<!-- JUnit 5 Integration -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>

<!-- Keycloak Container (for Exercise 2) -->
<dependency>
    <groupId>com.github.dasniko</groupId>
    <artifactId>testcontainers-keycloak</artifactId>
    <version>3.3.0</version>
    <scope>test</scope>
</dependency>
```

### 1.2 Create the Integration Test Class

**File:** `user-service/src/test/java/com/dornach/user/UserServiceContainerTest.java`

Create the test class with proper annotations and a PostgreSQL container:

```java
package com.dornach.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("docker")
@Testcontainers(disabledWithoutDocker = true)
class UserServiceContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("userdb")
            .withUsername("test")
            .withPassword("test");

    @Test
    @DisplayName("GET /actuator/health should be public")
    void healthEndpoint_ShouldBePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

### 1.3 Run Your First Container Test

```bash
cd user-service
mvn test -Dtest=UserServiceContainerTest
```

**First run:** Will download Docker images (may take a few minutes).

**Expected output:**
```
INFO  Creating container for image: postgres:16-alpine
INFO  Container started in PT0.886817S
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

<details>
<summary>💡 Understanding the annotations</summary>

| Annotation | Purpose |
|------------|---------|
| `@SpringBootTest` | Loads the full application context |
| `@AutoConfigureMockMvc` | Sets up MockMvc for HTTP testing |
| `@ActiveProfiles("docker")` | Uses the docker profile (JWT security enabled) |
| `@Testcontainers` | Manages container lifecycle |
| `@Container` | Marks a container to be managed by JUnit |
| `@ServiceConnection` | **Spring Boot 3.1+ magic** - Auto-configures `spring.datasource.*` |

</details>

---

## Exercise 2: Add Keycloak Container for JWT Testing

Now let's add Keycloak to test real JWT authentication.

### 2.1 Copy Realm Configuration

The test needs the same Keycloak realm as our development environment:

```bash
cp infra/keycloak/dornach-realm.json user-service/src/test/resources/
```

### 2.2 Add Keycloak Container and Dynamic Properties

Update your test class to add the Keycloak container and configure Spring Security dynamically.

**Add these imports:**
```java
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

**Add the Keycloak container after the PostgreSQL container:**
```java
@Container
static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
        .withRealmImportFile("dornach-realm.json");
```

**Add dynamic property configuration:**
```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    String issuerUri = keycloak.getAuthServerUrl() + "/realms/dornach";

    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
    registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> issuerUri + "/protocol/openid-connect/certs");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.sql.init.mode", () -> "never");
}
```

<details>
<summary>💡 Why @DynamicPropertySource for Keycloak?</summary>

Unlike PostgreSQL, Keycloak doesn't have a `@ServiceConnection` integration in Spring Boot.

We need `@DynamicPropertySource` to inject the dynamic Keycloak URL (with random port) into Spring Security configuration.

</details>

### 2.3 Add Token Helper Method

Add this helper method to obtain JWT tokens from Keycloak:

```java
private String getAccessToken(String username, String password) throws Exception {
    String tokenUrl = keycloak.getAuthServerUrl() + "/realms/dornach/protocol/openid-connect/token";

    String requestBody = String.format(
            "client_id=dornach-web&username=%s&password=%s&grant_type=password",
            username, password
    );

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

    var jsonNode = objectMapper.readTree(response.body());
    return jsonNode.get("access_token").asText();
}
```

### 2.4 Verify Keycloak Starts

Run the tests again to ensure Keycloak starts properly:

```bash
mvn test -Dtest=UserServiceContainerTest
```

**Expected output:**
```
INFO  Creating container for image: quay.io/keycloak/keycloak:26.0
INFO  Container started in PT15.817453S
INFO  Creating container for image: postgres:16-alpine
INFO  Container started in PT0.886817S
```

---

## Exercise 3: Write Integration Tests

Now write tests that verify real JWT authentication against the Keycloak container.

### 3.1 Test: Request Without Token Returns 401

Add this test to verify unauthenticated requests are rejected:

```java
@Test
@DisplayName("GET /users without token should return 401")
void getUsersWithoutToken_ShouldReturn401() throws Exception {
    mockMvc.perform(get("/users"))
            .andExpect(status().isUnauthorized());
}
```

### 3.2 Test: Request With Valid Token Returns 200

Add this test to verify authenticated requests succeed:

```java
@Test
@DisplayName("GET /users with valid token should return 200")
void getUsersWithValidToken_ShouldReturn200() throws Exception {
    String token = getAccessToken("alice", "alice123");

    mockMvc.perform(get("/users")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
}
```

### 3.3 Test: Create User With Admin Token

Add this test to verify admin operations work:

```java
@Test
@DisplayName("POST /users with admin token should create user")
void createUserWithAdminToken_ShouldReturn201() throws Exception {
    String token = getAccessToken("bob", "bob123");

    String requestBody = """
            {
                "email": "newuser@dornach.com",
                "firstName": "New",
                "lastName": "User",
                "role": "EMPLOYEE"
            }
            """;

    mockMvc.perform(post("/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("newuser@dornach.com"));
}
```

### 3.4 Run All Tests

```bash
mvn test -Dtest=UserServiceContainerTest
```

**Expected output:**
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Challenge: Test M2M Authentication (Optional)

Write a test that uses **client credentials** (M2M) instead of user credentials.

**Hint:** Create a helper method similar to `getAccessToken` but with:
```java
String requestBody = "client_id=order-service-client" +
        "&client_secret=order-service-secret" +
        "&grant_type=client_credentials";
```

---

## Troubleshooting

### "Could not find a valid Docker environment"

Docker is not running or not accessible.
```bash
# Verify Docker
docker ps

# If using Colima (macOS)
export DOCKER_HOST=unix:///Users/$USER/.colima/default/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

### Tests are slow

First run downloads images. Subsequent runs are faster.

To reuse containers between runs:
1. Create `~/.testcontainers.properties`
2. Add: `testcontainers.reuse.enable=true`

---

## Validation Checklist

Before moving to Bonus B, verify:

- [ ] Dependencies added to pom.xml
- [ ] Test class created with `@Testcontainers` and `@ServiceConnection`
- [ ] PostgreSQL container starts successfully
- [ ] Keycloak container starts with realm imported
- [ ] Token retrieval helper works
- [ ] All 4 tests pass
- [ ] Containers are cleaned up after tests

---

## Summary

In this exercise, you learned:

| Concept | What You Did |
|---------|--------------|
| **Testcontainers** | Ran real Docker containers for tests |
| **@ServiceConnection** | Auto-configured PostgreSQL datasource |
| **@DynamicPropertySource** | Injected Keycloak URL dynamically |
| **Real JWT validation** | Tested auth with real Keycloak tokens |
| **Ephemeral containers** | Each test run = clean state |

---

## Before Moving On

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Bonus A"
```

**Option B:** You need to catch up
```bash
git stash && git checkout bonus-a-complete
```

**Next:** [Bonus B - Async SQS](./BONUS_B_ASYNC_SQS.md)
