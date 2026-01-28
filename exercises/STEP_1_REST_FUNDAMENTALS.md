# Step 1: REST Fundamentals & Java 21 Modernity

## Objectives

By the end of this exercise, you will:
- Complete a JPA Entity with annotations and timestamps
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

> **Warning:** Use `@Table(name = "users")` with an **"s"**. The word `user` is a reserved keyword in PostgreSQL and will cause a 500 error.

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

> **Important:** The `UserService` currently uses getter syntax (`request.getEmail()`). When you convert the DTOs to records, you'll need to update `UserService` to use record accessor syntax (`request.email()`).

### 2.1 CreateUserRequest

Create a **record** with the following fields:
- `email` (String, required, must be valid email)
- `firstName` (String, required, 2-50 characters)
- `lastName` (String, required, 2-50 characters)
- `role` (String, required)

Use Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`) with custom error messages.

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
- A static `from(User user)` method to convert an entity to a response DTO

> **Note:** The `UserController` uses `UserResponse.from(user)` to map entities to DTOs. Without this method, the code won't compile.

**Question:** Why do we use separate DTOs for request and response?

### 2.3 UpdateUserRequest

Create a record for updating users. Unlike `CreateUserRequest`:
- `email` should **not** be updatable (omit it)
- Only include: `firstName`, `lastName`, `role`

```java
public record UpdateUserRequest(
    // Add fields with validation annotations
) {}
```

> **Note:** The `UserController.updateUser()` currently uses `CreateUserRequest`. Once you create `UpdateUserRequest`, update the controller to use it instead.

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

> **Note:** Don't use `if (user == null)`. Use a try-catch to catch the exception thrown by `UserService` and return 404.

<details>
<summary>💡 Hint: Try-catch approach</summary>

```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
    try {
        var user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    } catch (RuntimeException e) {
        return ResponseEntity.notFound().build();
    }
}
```

</details>

**Validation:** Test with Bruno or curl:
```bash
# Create a user
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@dornach.com","firstName":"Alice","lastName":"Martin","role":"EMPLOYEE"}'

# Should return 201 with the created user

# Test 404
curl http://localhost:8081/users/00000000-0000-0000-0000-000000000000
# Should return 404
```

---

## Exercise 4: Error Handling with RFC 7807

**File:** `user-service/src/main/java/com/dornach/user/exception/GlobalExceptionHandler.java`

Implement a global exception handler that returns errors in RFC 7807 format.

> **Important:** Once you complete this exercise, you can **remove the try-catch from 3.2**. The `@RestControllerAdvice` will handle the exception globally, so your controller just needs to call `userService.getUserById(id)` and let the exception propagate.

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

**Validation:** Test with invalid data:
```bash
curl -X POST http://localhost:8081/users \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid","firstName":"","lastName":"","role":""}'
# Should return 400 with RFC 7807 format and field errors
```

### 4.2 Handle User Not Found

1. Create a custom exception `UserNotFoundException` in the `exception` package
2. Add a handler for it in `GlobalExceptionHandler` that returns 404 with RFC 7807 format
3. Update `UserService.getUserById()` to throw `UserNotFoundException` instead of `RuntimeException`

<details>
<summary>💡 Hint: UserNotFoundException handler</summary>

```java
@ExceptionHandler(UserNotFoundException.class)
public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setType(URI.create("https://api.dornach.com/errors/user-not-found"));
    problem.setTitle("User Not Found");
    problem.setDetail(ex.getMessage());
    return problem;
}
```

</details>

**Validation:** Test with an invalid user ID:
```bash
curl http://localhost:8081/users/00000000-0000-0000-0000-000000000000
# Should return 404 with RFC 7807 format
```

---

## Exercise 5: Enable Virtual Threads

**File:** `user-service/src/main/resources/application.yaml`

### 5.1 Enable Virtual Threads

Add the configuration to enable Virtual Threads:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### 5.2 Verify Virtual Threads are Enabled

Add a temporary endpoint in `UserController` to verify threads are virtual:

```java
@GetMapping("/debug/thread")
public Map<String, Object> threadInfo() {
    Thread current = Thread.currentThread();
    return Map.of(
        "threadName", current.getName(),
        "isVirtual", current.isVirtual()
    );
}
```

Test it:
```bash
curl http://localhost:8081/users/debug/thread
# Should return: {"threadName":"tomcat-handler-0","isVirtual":true}
```

> **Note:** Remove this endpoint before production!

### 5.3 When are Virtual Threads Useful?

| Workload Type | Virtual Threads Help? | Why |
|---------------|----------------------|-----|
| **I/O-bound** (HTTP calls, DB queries) | Yes | Thread waits for I/O, can handle millions of waiting threads |
| **CPU-bound** (calculations, compression) | No | CPU is the bottleneck, not thread count |

**In this training:** Virtual Threads shine in Step 2 when `order-service` calls `user-service` and `shipment-service`. Each HTTP call blocks the thread, but with Virtual Threads, thousands of concurrent requests are handled efficiently.

**Question:** What is the benefit of Virtual Threads over platform threads?

<details>
<summary>💡 Answer</summary>

Virtual Threads are lightweight (few KB vs 1MB for platform threads). This allows handling millions of concurrent requests without the complexity of reactive programming (WebFlux).

Key advantages:
- **Simple code**: Keep using blocking I/O (no Mono/Flux)
- **Scalability**: Handle thousands of concurrent requests
- **No callback hell**: Sequential code that's easy to read and debug

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
