# Step 1: REST Fundamentals & Java 21 Modernity

## Objectives

By the end of this exercise, you will:
- Create DTOs using Java Records
- Implement a REST controller with proper HTTP semantics
- Add Bean Validation to your DTOs
- Handle errors using RFC 7807 (Problem Details)
- Enable Virtual Threads for better scalability

---

## Prerequisites

- Java 21 installed
- IDE with Spring Boot support (IntelliJ, VS Code)
- Maven 3.9+
- **Bruno** installed (https://www.usebruno.com/downloads)
- Basic knowledge of Spring Boot

---

## Context

You are building the **user-service** microservice for Dornach, a logistics company. This service manages user accounts (employees, admins, managers).

The service should expose a REST API to:
- Create users
- List all users
- Get a user by ID
- Update a user
- Delete a user

---

## Exercise 1: Create the User Entity

**File:** `user-service/src/main/java/com/dornach/user/domain/User.java`

The `User` entity is partially implemented. Complete it with:
- JPA annotations (`@Entity`, `@Table`, `@Id`, etc.)
- An enum `UserRole` with values: `EMPLOYEE`, `MANAGER`, `ADMIN`
- Timestamps for `createdAt` and `updatedAt`

<details>
<summary>💡 Hint 1</summary>

Use `@GeneratedValue(strategy = GenerationType.UUID)` for automatic UUID generation.

</details>

<details>
<summary>💡 Hint 2</summary>

For timestamps, use `@CreationTimestamp` and `@UpdateTimestamp` from Hibernate.

</details>

**Validation:** The entity should compile without errors.

---

## Exercise 2: Create DTOs with Java Records

**Files to create:**
- `dto/CreateUserRequest.java`
- `dto/UpdateUserRequest.java`
- `dto/UserResponse.java`

### 2.1 CreateUserRequest

Create a **record** with the following fields:
- `email` (String, required, must be valid email)
- `firstName` (String, required, 2-50 characters)
- `lastName` (String, required, 2-50 characters)
- `role` (String, required)

Use Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`).

```java
// TODO: Complete this record
public record CreateUserRequest(
    // Add fields with validation annotations
) {}
```

<details>
<summary>💡 Hint</summary>

```java
public record CreateUserRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    String firstName,

    // ... continue
) {}
```

</details>

### 2.2 UserResponse

Create a record that represents the API response. It should include:
- All user fields (id, email, firstName, lastName, role, status)
- Timestamps (createdAt, updatedAt)

**Question:** Why do we use separate DTOs for request and response?

---

## Exercise 3: Implement the UserController

**File:** `user-service/src/main/java/com/dornach/user/controller/UserController.java`

Implement the following endpoints:

| Method | Path | Description | Response Code |
|--------|------|-------------|---------------|
| POST | `/users` | Create a user | 201 Created |
| GET | `/users` | List all users | 200 OK |
| GET | `/users/{id}` | Get user by ID | 200 OK / 404 Not Found |
| PUT | `/users/{id}` | Update user | 200 OK / 404 Not Found |
| DELETE | `/users/{id}` | Delete user | 204 No Content |

### 3.1 Create User Endpoint

```java
@PostMapping
public ResponseEntity<UserResponse> createUser(
    @Valid @RequestBody CreateUserRequest request
) {
    // TODO:
    // 1. Call userService.createUser(request)
    // 2. Return 201 Created with the user in the body
    // 3. Add Location header with the new user's URI
}
```

<details>
<summary>💡 Hint: Location header</summary>

```java
URI location = ServletUriComponentsBuilder
    .fromCurrentRequest()
    .path("/{id}")
    .buildAndExpand(user.getId())
    .toUri();

return ResponseEntity.created(location).body(userResponse);
```

</details>

### 3.2 Get User by ID

Handle the case where the user is not found. Return a proper 404 response.

**Validation:** Test with Bruno or curl:
```bash
# Create a user
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@dornach.com","firstName":"Alice","lastName":"Martin","role":"EMPLOYEE"}'

# Should return 201 with the created user
```

---

## Exercise 4: Error Handling with RFC 7807

**File:** `user-service/src/main/java/com/dornach/user/exception/GlobalExceptionHandler.java`

Implement a global exception handler that returns errors in RFC 7807 format.

### 4.1 Handle Validation Errors

When `@Valid` fails, Spring throws `MethodArgumentNotValidException`. Catch it and return:

```json
{
  "type": "https://api.dornach.com/errors/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "One or more fields failed validation",
  "instance": "/users",
  "errors": {
    "email": "Email must be valid",
    "firstName": "First name is required"
  }
}
```

<details>
<summary>💡 Hint: Using ProblemDetail</summary>

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setType(URI.create("https://api.dornach.com/errors/validation-error"));
    problem.setTitle("Validation Error");

    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error ->
        errors.put(error.getField(), error.getDefaultMessage())
    );
    problem.setProperty("errors", errors);

    return problem;
}
```

</details>

### 4.2 Handle User Not Found

Create a custom exception `UserNotFoundException` and handle it to return 404.

**Validation:** Test with an invalid user ID:
```bash
curl http://localhost:8081/users/00000000-0000-0000-0000-000000000000
# Should return 404 with RFC 7807 format
```

---

## Exercise 5: Enable Virtual Threads

**File:** `user-service/src/main/resources/application.yaml`

Add the configuration to enable Virtual Threads:

```yaml
spring:
  threads:
    virtual:
      enabled: ???  # What value?
```

**Question:** What is the benefit of Virtual Threads over platform threads?

<details>
<summary>💡 Answer</summary>

Virtual Threads are lightweight (few KB vs 1MB for platform threads). This allows handling millions of concurrent requests without the complexity of reactive programming (WebFlux).

</details>

---

## Challenge: Sealed Interface for Operation Results (Optional)

Instead of throwing exceptions, implement a sealed interface to represent operation results:

```java
public sealed interface UserOperationResult permits
    UserOperationResult.Success,
    UserOperationResult.NotFound,
    UserOperationResult.ValidationError,
    UserOperationResult.Conflict {

    record Success(UserResponse user) implements UserOperationResult {}
    record NotFound(UUID userId) implements UserOperationResult {}
    record ValidationError(String message) implements UserOperationResult {}
    record Conflict(String message) implements UserOperationResult {}
}
```

Then use pattern matching in your controller:

```java
return switch (result) {
    case Success(var user) -> ResponseEntity.ok(user);
    case NotFound(var id) -> ResponseEntity.notFound().build();
    case ValidationError(var msg) -> ResponseEntity.badRequest().body(msg);
    case Conflict(var msg) -> ResponseEntity.status(409).body(msg);
};
```

---

## Validation with Bruno

### Setup (first time only)

1. Open Bruno
2. Click "Open Collection" and select the `bruno/` folder
3. Select the **Direct** environment (bottom-left dropdown)

### Run Step 1 Tests

1. Open the collection **Step 1 - REST Fundamentals / User Service**
2. Run the following requests:
   - **Health Check** → Should return 200 with `{"status": "UP"}`
   - **List Users (requires JWT)** → Skip for now (JWT comes in Step 5)
   - **OpenAPI Spec** → Should return 200 with the OpenAPI JSON

```
bruno/
└── Step 1 - REST Fundamentals/
    └── User Service/
        ├── Health Check.bru        <-- Run this
        ├── List Users.bru          (skip - needs JWT)
        └── OpenAPI Spec.bru        <-- Run this
```

### Manual Testing with curl

```bash
# Health check
curl http://localhost:8081/actuator/health

# Create a user
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@dornach.com","firstName":"Test","lastName":"User","role":"EMPLOYEE"}'

# List users
curl http://localhost:8081/users

# Test validation error
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid","firstName":"","lastName":"","role":""}'
# Should return 400 with RFC 7807 format
```

---

## Validation Checklist

Before moving to Step 2, verify:

- [ ] Bruno: Health Check returns 200
- [ ] Bruno: OpenAPI Spec returns valid JSON
- [ ] `POST /users` creates a user and returns 201
- [ ] `GET /users` returns a list of users
- [ ] `GET /users/{id}` returns 404 for unknown IDs
- [ ] Validation errors return RFC 7807 format
- [ ] Virtual Threads are enabled (check logs at startup)

**Run the unit tests:**
```bash
cd user-service
mvn test
```

All tests in `UserControllerIntegrationTest` should pass.

---

## Summary

In this step, you learned:
- **Java Records** simplify DTO creation (immutable, concise)
- **Bean Validation** declaratively validates input
- **RFC 7807** standardizes error responses
- **Virtual Threads** improve scalability without reactive complexity

---

## Before Moving On

Make sure you're ready for Step 2:

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 1"
```

**Option B:** You need to catch up
```bash
git checkout step-1-complete
```

**Next:** [Step 2 - Service Communication](./STEP_2_SERVICE_COMMUNICATION.md)
