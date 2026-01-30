# Bonus A: Integration Tests with Testcontainers

---

## Recap: Step 8

In Step 8, you deployed to **AWS Lambda**:
- **Spring Cloud Function** for portable serverless functions
- **Shaded JAR** with all dependencies bundled
- **LocalStack Lambda** for local development
- **API Gateway v2** as HTTP entry point
- **Cold Start** challenge and solutions

Now let's learn how to test our microservices with real containers.

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
- **Bruno** installed (https://www.usebruno.com/downloads)
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

## Exercise 1: Add Testcontainers Dependencies

**File:** `user-service/pom.xml`

### 1.1 Add Dependencies

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

<!-- Keycloak Container -->
<dependency>
    <groupId>com.github.dasniko</groupId>
    <artifactId>testcontainers-keycloak</artifactId>
    <version>3.3.0</version>
    <scope>test</scope>
</dependency>
```

---

## Exercise 2: Create Integration Test Class

**File:** `user-service/src/test/java/com/dornach/user/UserServiceContainerTest.java`

### 2.1 Create the Test Class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("docker")
@Testcontainers(disabledWithoutDocker = true)
class UserServiceContainerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // TODO: Add containers in next step
}
```

<details>
<summary>💡 Explanation</summary>

- `@SpringBootTest` - Loads the full application context
- `@AutoConfigureMockMvc` - Sets up MockMvc for HTTP testing
- `@ActiveProfiles("docker")` - Uses the docker profile (JWT security enabled)
- `@Testcontainers` - Manages container lifecycle
- `disabledWithoutDocker = true` - Skips tests if Docker unavailable

</details>

---

## Exercise 3: Add PostgreSQL Container

### 3.1 Add Container Definition

```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("userdb")
        .withUsername("test")
        .withPassword("test");
```

**Question:** What does `@ServiceConnection` do?

<details>
<summary>💡 Answer</summary>

`@ServiceConnection` is Spring Boot 3.1+ magic. It automatically configures:
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

You don't need `@DynamicPropertySource` for the datasource!

</details>

---

## Exercise 4: Add Keycloak Container

### 4.1 Copy Realm Configuration

Copy the realm configuration for tests:
```bash
cp infra/keycloak/dornach-realm.json user-service/src/test/resources/
```

### 4.2 Add Keycloak Container

```java
@Container
static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
        .withRealmImportFile("dornach-realm.json");
```

### 4.3 Configure Dynamic Properties

Keycloak doesn't have `@ServiceConnection`, so we need `@DynamicPropertySource`:

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

---

## Exercise 5: Create Token Helper Method

### 5.1 Add Token Retrieval Method

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

---

## Exercise 6: Write Integration Tests

### 6.1 Test: Request Without Token Returns 401

```java
@Test
@DisplayName("GET /users without token should return 401")
void getUsersWithoutToken_ShouldReturn401() throws Exception {
    mockMvc.perform(get("/users"))
            .andExpect(status().isUnauthorized());
}
```

### 6.2 Test: Request With Valid Token Returns 200

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

### 6.3 Test: Create User With Admin Token

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

### 6.4 Test: Health Endpoint Is Public

```java
@Test
@DisplayName("GET /actuator/health should be public")
void healthEndpoint_ShouldBePublic() throws Exception {
    mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
}
```

---

## Exercise 7: Run the Tests

### 7.1 Run with Maven

```bash
cd user-service
mvn test -Dtest=UserServiceContainerTest
```

**First run:** Will download Docker images (may take a few minutes).

### 7.2 Expected Output

```
[INFO] Running com.dornach.user.UserServiceContainerTest
INFO  Creating container for image: quay.io/keycloak/keycloak:26.0
INFO  Container started in PT15.817453S
INFO  Creating container for image: postgres:16-alpine
INFO  Container started in PT0.886817S

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Challenge: Test M2M Authentication (Optional)

Write a test that:
1. Gets an M2M token using client credentials
2. Calls a protected endpoint
3. Verifies the response

<details>
<summary>💡 Hint</summary>

```java
private String getM2MToken() throws Exception {
    String tokenUrl = keycloak.getAuthServerUrl() + "/realms/dornach/protocol/openid-connect/token";

    String requestBody = "client_id=order-service-client" +
            "&client_secret=order-service-secret" +
            "&grant_type=client_credentials";

    // ... similar to getAccessToken()
}
```

</details>

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
- [ ] Test class created with proper annotations
- [ ] PostgreSQL container starts with `@ServiceConnection`
- [ ] Keycloak container starts with realm imported
- [ ] Token retrieval helper works
- [ ] All 4 tests pass
- [ ] Containers are cleaned up after tests

---

## Summary

In this exercise, you learned:
- **Testcontainers** runs real Docker containers for tests
- **@ServiceConnection** auto-configures Spring Boot datasources
- **@DynamicPropertySource** injects dynamic properties
- **Real JWT validation** catches auth bugs that mocks miss
- **Containers are ephemeral** - clean state for each test run

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
