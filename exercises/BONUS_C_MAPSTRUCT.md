# Bonus C: DTO Mapping with MapStruct

> **Prerequisites:** Complete Bonus B (or checkout `bonus-b-complete`)
> ```bash
> git stash && git checkout bonus-b-complete
> ```

## Objectives

By the end of this exercise, you will:
- Understand why MapStruct is preferred over manual mapping
- Configure MapStruct in a Spring Boot project
- Create type-safe mappers for DTOs and entities
- Use dependency injection with MapStruct mappers

## Context

Currently, our services use manual mapping methods like `UserResponse.from(User user)`. While this works, it has drawbacks:

- **Boilerplate code** - Repetitive mapping logic in every DTO
- **Error-prone** - Easy to forget fields when entities change
- **No compile-time safety** - Mistakes only discovered at runtime

**MapStruct** generates mapping code at compile time, providing:
- Type-safe mappings with compile-time validation
- Better performance than reflection-based mappers
- Clean, readable generated code
- Spring integration for dependency injection

---

## Exercise 1: Add MapStruct to user-service

### 1.1 Add Dependencies

Add MapStruct to `user-service/pom.xml`:

```xml
<properties>
    <!-- Add this property -->
    <mapstruct.version>1.5.5.Final</mapstruct.version>
</properties>

<dependencies>
    <!-- Add MapStruct dependency -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
</dependencies>
```

### 1.2 Configure the Compiler Plugin

Add the annotation processor to the `maven-compiler-plugin`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
                <compilerArgs>
                    <arg>-Amapstruct.defaultComponentModel=spring</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

<details>
<summary>💡 Hint: What does defaultComponentModel=spring do?</summary>

This tells MapStruct to generate mappers as Spring `@Component` beans, allowing you to inject them with `@Autowired` or constructor injection.

</details>

### 1.3 Create UserMapper

Create `user-service/src/main/java/com/dornach/user/mapper/UserMapper.java`:

```java
package com.dornach.user.mapper;

import com.dornach.user.domain.User;
import com.dornach.user.dto.CreateUserRequest;
import com.dornach.user.dto.UpdateUserRequest;
import com.dornach.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(CreateUserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);
}
```

### 1.4 Update UserService

Inject and use the mapper in `UserService.java`:

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;  // Add this

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;  // Add this
    }

    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        User user = userMapper.toEntity(request);  // Use mapper
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    // Update other methods similarly...
}
```

### 1.5 Update UserController

Replace manual mapping with the mapper:

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;  // Add this

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;  // Add this
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers().stream()
                .map(userMapper::toResponse)  // Use mapper
                .toList();
    }

    // Update other methods similarly...
}
```

### 1.6 Remove Manual Mapping Methods

You can now remove the `from()` method from `UserResponse.java` since MapStruct handles the mapping.

### 1.7 Build and Test

```bash
# Compile to generate mapper implementation
mvn clean compile -pl user-service

# Check generated code (optional)
ls user-service/target/generated-sources/annotations/com/dornach/user/mapper/

# Run tests
mvn test -pl user-service
```

---

## Exercise 2: Add MapStruct to order-service

Apply the same pattern to `order-service`:

1. Add MapStruct dependencies to `pom.xml`
2. Create `OrderMapper.java`:

```java
@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderResponse toResponse(Order order);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "trackingNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Order toEntity(CreateOrderRequest request);
}
```

3. Update `OrderService` and `OrderController`

---

## Exercise 3: Add MapStruct to shipment-service

Apply the same pattern to `shipment-service`:

1. Add MapStruct dependencies to `pom.xml`
2. Create `ShipmentMapper.java`
3. Update `ShipmentService` and `ShipmentController`

---

## Challenge: Custom Mapping Methods

Sometimes you need custom logic in mappings. Add a method to format the user's full name:

```java
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName", source = "user")
    UserResponse toResponse(User user);

    default String mapFullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
```

Then add `fullName` field to `UserResponse`:

```java
public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String fullName,  // New field
    UserRole role,
    UserStatus status
) {}
```

---

## Validation

Run all tests to ensure mappings work correctly:

```bash
mvn clean test
```

Start the services and test with Bruno:
- All existing API calls should work identically
- No functional changes, only internal refactoring

---

## Validation Checklist

- [ ] MapStruct configured in all 3 services
- [ ] Mappers created for User, Order, and Shipment
- [ ] Services and Controllers use injected mappers
- [ ] Manual `from()` methods removed
- [ ] All tests pass
- [ ] API responses unchanged

---

## Summary

You learned:
- **MapStruct configuration** with Spring Boot and Maven
- **Mapper interfaces** with `@Mapper` annotation
- **Field mapping** with `@Mapping` for ignoring/customizing fields
- **Update mappings** with `@MappingTarget` for partial updates
- **Custom methods** for complex mapping logic

### Why MapStruct over alternatives?

| Approach | Pros | Cons |
|----------|------|------|
| Manual mapping | Simple, no dependencies | Boilerplate, error-prone |
| MapStruct | Type-safe, fast, compile-time | Build configuration |
| ModelMapper | Easy setup | Reflection-based, slower |
| Dozer | Feature-rich | Complex, XML config |

**MapStruct is the recommended approach** for production Spring Boot applications.
