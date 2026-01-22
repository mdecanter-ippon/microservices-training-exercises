# Step 3: Contract-First & API Documentation

> **⚠️ Before starting:** Make sure you have completed Step 2.
> If you need to catch up:
> ```bash
> git stash && git checkout step-2-complete
> ```

---

## Objectives

By the end of this exercise, you will:
- Configure springdoc-openapi for automatic API documentation
- Enrich endpoints with OpenAPI annotations
- Access and use Swagger UI
- Export OpenAPI specifications
- Generate type-safe clients from the specification

---

## Prerequisites

- Step 2 completed (service communication working)
- **Bruno** installed (https://www.usebruno.com/downloads)
- Services running (user-service, order-service)
- Basic understanding of API documentation

---

## Context

Your microservices need documentation. Instead of writing documentation manually (which becomes outdated), you'll generate it automatically from the code.

**Contract-First benefits:**
1. **Type Safety** - Generated clients detect breaking changes at compile time
2. **Developer Velocity** - IDE auto-completion, no manual HTTP calls
3. **Living Documentation** - Always synchronized with code

---

## Exercise 1: Configure OpenAPI

**File:** `user-service/src/main/java/com/dornach/user/config/OpenApiConfig.java`

### 1.1 Verify Dependencies

Check that `pom.xml` includes springdoc-openapi:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

### 1.2 Create the Configuration Class

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "???",           // What should be the API title?
        version = "???",         // What version?
        description = "Identity and user management service"
    )
)
public class OpenApiConfig {
}
```

<details>
<summary>💡 Hint</summary>

```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Dornach User Service API",
        version = "1.0.0",
        description = "Identity and user management service",
        contact = @Contact(name = "Engineering Team", email = "eng@dornach.com")
    ),
    servers = {
        @Server(url = "http://localhost:8081", description = "Local Development")
    }
)
public class OpenApiConfig {
}
```

</details>

### 1.3 Validate Configuration

Start the service and access Swagger UI:
```bash
cd user-service && mvn spring-boot:run
```

Open in browser: http://localhost:8081/swagger-ui.html

You should see:
- The API title and description
- All endpoints listed
- Models/schemas section

---

## Exercise 2: Enrich Endpoints with Annotations

**File:** `user-service/src/main/java/com/dornach/user/controller/UserController.java`

### 2.1 Add Operation Documentation

Update the `createUser` method with OpenAPI annotations:

```java
@PostMapping
@Operation(
    summary = "???",                    // Short description
    description = "???"                 // Detailed description
)
@ApiResponse(responseCode = "201", description = "User created")
@ApiResponse(responseCode = "400", description = "Validation error")
public ResponseEntity<UserResponse> createUser(...) {
```

<details>
<summary>💡 Hint</summary>

```java
@PostMapping
@Operation(
    summary = "Create a new user",
    description = "Creates a new user with the specified details. Email must be unique."
)
@ApiResponse(responseCode = "201", description = "User created successfully")
@ApiResponse(responseCode = "400", description = "Validation error - invalid input")
@ApiResponse(responseCode = "409", description = "Conflict - email already exists")
public ResponseEntity<UserResponse> createUser(
    @Valid @RequestBody CreateUserRequest request
) {
```

</details>

### 2.2 Add Request Examples

Add an example to help API consumers:

```java
@PostMapping
@Operation(
    summary = "Create a new user",
    requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(
            examples = @ExampleObject(
                name = "Employee",
                value = """
                    {
                      "email": "alice@dornach.com",
                      "firstName": "Alice",
                      "lastName": "Martin",
                      "role": "EMPLOYEE"
                    }
                    """
            )
        )
    )
)
```

### 2.3 Validate in Swagger UI

1. Refresh http://localhost:8081/swagger-ui.html
2. Expand the `POST /users` endpoint
3. Click "Try it out"
4. Verify the example is pre-filled

---

## Exercise 3: Add Security Scheme

**File:** `user-service/src/main/java/com/dornach/user/config/OpenApiConfig.java`

Add JWT security documentation (you'll implement actual security in Step 5):

```java
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT token obtained from Keycloak"
)
public class OpenApiConfig {
}
```

In the controller, mark endpoints as requiring authentication:

```java
@Operation(
    summary = "Get all users",
    security = @SecurityRequirement(name = "bearerAuth")
)
@GetMapping
public List<UserResponse> getAllUsers() {
```

**Validation:** Swagger UI should now show a "Lock" icon and an "Authorize" button.

---

## Exercise 4: Export OpenAPI Specification

### 4.1 Get the Specification

While the service is running:

```bash
# JSON format
curl http://localhost:8081/v3/api-docs

# YAML format (more readable)
curl http://localhost:8081/v3/api-docs.yaml
```

### 4.2 Save to File

Create a directory and export:

```bash
mkdir -p openapi
curl http://localhost:8081/v3/api-docs.yaml > openapi/user-service.yaml
```

### 4.3 Examine the Specification

Open `openapi/user-service.yaml` and find:
- The `paths` section (your endpoints)
- The `components/schemas` section (your DTOs)
- The `security` section (JWT configuration)

---

## Exercise 5: Generate a Client (Demonstration)

This exercise demonstrates how to generate clients. In real projects, this is typically done in CI/CD.

### 5.1 Install OpenAPI Generator (optional)

```bash
# macOS
brew install openapi-generator

# Or use npm
npm install -g @openapitools/openapi-generator-cli
```

### 5.2 Generate a Java Client

```bash
openapi-generator-cli generate \
    -i openapi/user-service.yaml \
    -g java \
    -o generated-clients/java/user-service-client \
    --additional-properties=library=restclient
```

### 5.3 Generated Client Usage

The generated code provides type-safe access:

```java
// Manual approach (error-prone)
RestClient.get().uri("/users/{id}", userId).retrieve().body(UserResponse.class);

// Generated client (type-safe)
UsersApi api = new UsersApi();
UserResponse user = api.getUserById(userId);  // Auto-completion!
```

**Question:** What happens if the API changes `firstName` to `fullName`?

<details>
<summary>💡 Answer</summary>

With a generated client, you get a **compile-time error** instead of a **runtime error**. The client code references `user.firstName`, which no longer exists. This is caught immediately during the build, not in production.

</details>

---

## Challenge: Document All Services (Optional)

Apply the same OpenAPI configuration to:
1. `order-service` - Document the order creation and confirmation endpoints
2. `shipment-service` - Document the shipment tracking endpoint

Add examples for each endpoint.

---

## Validation with Bruno

Step 3 focuses on documentation - validation is done via the OpenAPI spec itself.

### Check OpenAPI Spec Accessibility

```bash
# Test JSON endpoint
curl -s http://localhost:8081/v3/api-docs | jq '.info.title'
# Should output: "Dornach User Service API"

# Test YAML endpoint
curl -s http://localhost:8081/v3/api-docs.yaml | head -20
# Should show OpenAPI specification header
```

### Manual Validation

1. Open http://localhost:8081/swagger-ui.html
2. Verify all endpoints are documented
3. Click "Try it out" on `POST /users`
4. Verify the example is pre-filled
5. Execute the request and verify it works

---

## Validation Checklist

Before moving to Step 4, verify:

- [ ] Swagger UI accessible at `/swagger-ui.html`
- [ ] API title and description appear correctly
- [ ] All endpoints are documented with summaries
- [ ] `POST /users` has a request example
- [ ] Security scheme (bearerAuth) is configured
- [ ] "Authorize" button appears in Swagger UI
- [ ] OpenAPI spec exportable via `/v3/api-docs.yaml`

---

## Summary

In this step, you learned:
- **springdoc-openapi** generates documentation from annotations
- **@Operation** and **@ApiResponse** describe endpoints
- **@ExampleObject** provides request/response examples
- **@SecurityScheme** documents authentication requirements
- **OpenAPI Generator** creates type-safe clients in any language

---

## Before Moving On

Make sure you're ready for Step 4:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 3"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-3-complete
```

**Next:** [Step 4 - API Gateway](./STEP_4_API_GATEWAY.md)
