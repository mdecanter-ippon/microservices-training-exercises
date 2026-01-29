# Step 3: Contract-First & API Documentation

---

## Recap: Step 2

In Step 2, you implemented **service communication** in order-service:
- **RestClientConfig** with two `RestClient` beans (userRestClient, shipmentRestClient)
- **UserClient** interface + implementation to validate users exist
- **ShipmentClient** interface + implementation to create shipments
- **OrderService** orchestrating calls to both services
- **Resilience4j** retry and timeout for fault tolerance

> **Tip:** You can now run all integration tests in order-service to verify your implementation. The test class has more complete coverage than the single test from Exercise 7:
> ```bash
> cd order-service && mvn test
> ```

---

## Objectives

By the end of this exercise, you will:
- Configure springdoc-openapi for automatic API documentation
- Enrich endpoints with OpenAPI annotations
- Access and use Swagger UI
- Export OpenAPI specifications
- Understand how to generate type-safe clients from the specification

---

## Prerequisites

- Step 2 completed (service communication working)
- Services running (user-service, order-service)
- Basic understanding of API documentation

---

## Context

Your microservices need documentation. Instead of writing documentation manually (which becomes outdated), you'll generate it automatically from the code.

**Contract-First benefits:**
1. **Type Safety** - Generated clients detect breaking changes at compile time
2. **Developer Velocity** - Instead of manually configuring `RestClient` with URLs, headers, and response types, you can generate an `ApiClient` from the OpenAPI spec that handles all HTTP calls automatically with full IDE auto-completion
3. **Living Documentation** - Always synchronized with code (Swagger UI reflects your actual endpoints)

---

## Exercise 1: Configure OpenAPI

You'll create an OpenAPI configuration class for `user-service`.

### 1.1 Verify Dependencies

Check the **root `pom.xml`** (at the project root, not inside user-service). This parent POM defines shared dependencies that all microservices inherit:

```xml
<!-- In the root pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

This dependency provides:
- Automatic OpenAPI spec generation from your controllers
- Swagger UI at `/swagger-ui.html`
- JSON/YAML spec at `/v3/api-docs`

### 1.2 Create the Configuration Class

Create the file: `user-service/src/main/java/com/dornach/user/config/OpenApiConfig.java`

**Step 1: Create an empty configuration class**

```java
package com.dornach.user.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
}
```

Start user-service and open http://localhost:8081/swagger-ui.html

You should already see your endpoints listed, but without any custom metadata.

**Step 2: Add the @OpenAPIDefinition annotation**

Now add basic API information:

```java
package com.dornach.user.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Dornach User Service API",
        version = "1.0.0",
        description = "Identity and user management service"
    )
)
public class OpenApiConfig {
}
```

Restart the service and refresh Swagger UI. You should now see:
- Your custom title "Dornach User Service API"
- Version "1.0.0"
- The description

**Step 3: Add contact and server information**

```java
package com.dornach.user.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

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

Restart and verify the server URL appears in Swagger UI.

---

## Exercise 2: Enrich Endpoints with Annotations

Now let's improve the documentation for individual endpoints.

**File:** `user-service/src/main/java/com/dornach/user/controller/UserController.java`

### 2.1 Understand OpenAPI Annotations

Before coding, here's what each annotation does:

| Annotation | Purpose |
|------------|---------|
| `@Tag` | Groups endpoints under a category (shown as a collapsible section in Swagger UI) |
| `@Operation` | Describes what an endpoint does (summary + detailed description) |
| `@ApiResponse` | Documents possible HTTP responses (status codes, descriptions) |
| `@ExampleObject` | Provides example request/response bodies for "Try it out" |

### 2.2 Add @Tag to the Controller

Add a `@Tag` annotation to group all user endpoints:

```java
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User management operations")
public class UserController {
```

Restart and refresh Swagger UI. Your endpoints should now be grouped under "Users".

### 2.3 Add @Operation to createUser

Find the `createUser` method and add an `@Operation` annotation:

```java
import io.swagger.v3.oas.annotations.Operation;

@PostMapping
@Operation(
    summary = "Create a new user",
    description = "Creates a new user with the specified details. Email must be unique."
)
public ResponseEntity<UserResponse> createUser(
```

Restart and expand `POST /users` in Swagger UI. You should see your summary and description.

### 2.4 Add @ApiResponse Annotations

Document the possible responses:

```java
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@PostMapping
@Operation(
    summary = "Create a new user",
    description = "Creates a new user with the specified details. Email must be unique."
)
@ApiResponse(responseCode = "201", description = "User created successfully")
@ApiResponse(responseCode = "400", description = "Validation error - invalid input")
public ResponseEntity<UserResponse> createUser(
```

Restart and check the "Responses" section in Swagger UI for this endpoint.

### 2.5 Add a Request Example

Add an example to help API consumers:

```java
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@PostMapping
@Operation(
    summary = "Create a new user",
    description = "Creates a new user with the specified details. Email must be unique.",
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
@ApiResponse(responseCode = "201", description = "User created successfully")
@ApiResponse(responseCode = "400", description = "Validation error - invalid input")
public ResponseEntity<UserResponse> createUser(
```

Restart and click "Try it out" on `POST /users`. The example should be pre-filled.

### 2.6 Verify Everything Works

1. Refresh http://localhost:8081/swagger-ui.html
2. Expand the `POST /users` endpoint
3. Click "Try it out"
4. Verify the example is pre-filled
5. Execute the request and verify it works

<details>
<summary>Full solution for createUser</summary>

```java
@PostMapping
@Operation(
    summary = "Create a new user",
    description = "Creates a new user with the specified details. Email must be unique.",
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
@ApiResponse(responseCode = "201", description = "User created successfully")
@ApiResponse(responseCode = "400", description = "Validation error - invalid input")
public ResponseEntity<UserResponse> createUser(
        @Valid @RequestBody CreateUserRequest request) {
    var user = userService.createUser(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
}
```

</details>

---

## Exercise 3: Export OpenAPI Specification

The OpenAPI spec is what makes contract-first development possible.

### 3.1 Get the Specification

While the service is running:

```bash
# JSON format
curl http://localhost:8081/v3/api-docs

# YAML format (more readable)
curl http://localhost:8081/v3/api-docs.yaml
```

### 3.2 Save to File

Create a directory and export:

```bash
mkdir -p openapi
curl http://localhost:8081/v3/api-docs.yaml > openapi/user-service.yaml
```

### 3.3 Examine the Specification

Open `openapi/user-service.yaml` and find:
- The `info` section (your title, version, description)
- The `paths` section (your endpoints)
- The `components/schemas` section (your DTOs: CreateUserRequest, UserResponse, etc.)

---

## Exercise 4: Client Generation (Demonstration)

This exercise demonstrates how generated clients work. In real projects, this is typically done in CI/CD.

### 4.1 The Problem with Manual HTTP Calls

When you manually configure `RestClient`:

```java
// Manual approach - error-prone
RestClient.get()
    .uri("/users/{id}", userId)
    .retrieve()
    .body(UserResponse.class);  // What if UserResponse changes?
```

**Problems:**
- No compile-time safety if the API changes
- No auto-completion for fields
- Easy to make typos in URLs

### 4.2 Generated Clients Solve This

From the OpenAPI spec, you can generate a type-safe client:

```java
// Generated client - type-safe
UsersApi api = new UsersApi();
UserResponse user = api.getUserById(userId);  // Auto-completion!
```

**Question:** What happens if the API changes `firstName` to `fullName`?

<details>
<summary>Answer</summary>

With a generated client, you get a **compile-time error** instead of a **runtime error**. The client code references `user.firstName`, which no longer exists. This is caught immediately during the build, not in production.

</details>

### 4.3 How to Generate (Optional)

If you want to try it:

```bash
# Install OpenAPI Generator
brew install openapi-generator  # macOS

# Generate a Java client
openapi-generator generate \
    -i openapi/user-service.yaml \
    -g java \
    -o generated-clients/java/user-service-client \
    --additional-properties=library=restclient
```

---

## Challenge: Document Other Endpoints (Optional)

Apply the same OpenAPI annotations to:
1. `GET /users` - List all users
2. `GET /users/{id}` - Get user by ID
3. `PUT /users/{id}` - Update user
4. `DELETE /users/{id}` - Delete user

Add examples for each endpoint.

---

## Validation Checklist

Before moving to Step 4, verify:

- [ ] Swagger UI accessible at `/swagger-ui.html`
- [ ] API title and description appear correctly
- [ ] `POST /users` has @Operation with summary
- [ ] `POST /users` has @ApiResponse annotations
- [ ] `POST /users` has a request example
- [ ] OpenAPI spec exportable via `/v3/api-docs.yaml`

---

## Summary

In this step, you learned:
- **springdoc-openapi** generates documentation from annotations
- **@OpenAPIDefinition** configures API metadata
- **@Tag** groups endpoints
- **@Operation** and **@ApiResponse** describe endpoints
- **@ExampleObject** provides request/response examples
- **OpenAPI Generator** can create type-safe clients in any language

> **Note:** Security annotations (@SecurityScheme, @SecurityRequirement) will be covered in Step 5 when you implement actual authentication.

---

## Before Moving On

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 3"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-3-complete
```

**Next:** [Step 4 - API Gateway](./STEP_4_API_GATEWAY.md)
