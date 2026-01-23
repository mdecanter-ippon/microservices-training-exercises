package com.dornach.user.controller;

import com.dornach.user.dto.CreateUserRequest;
import com.dornach.user.dto.UpdateUserRequest;
import com.dornach.user.dto.UserResponse;
import com.dornach.user.mapper.UserMapper;
import com.dornach.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(
        name = "Users",
        description = "User management endpoints for creating, retrieving, updating, and deleting users"
)
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @PostMapping
    @Operation(
            summary = "Create a new user",
            description = "Creates a new user with the specified details. Email must be unique.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateUserRequest.class),
                            examples = @ExampleObject(
                                    name = "Employee",
                                    value = """
                                            {
                                              "email": "alice.martin@dornach.com",
                                              "firstName": "Alice",
                                              "lastName": "Martin",
                                              "role": "EMPLOYEE"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class)
            )
    )
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        var user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponse(user));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get user by ID",
            description = "Retrieves a user by their unique identifier"
    )
    @ApiResponse(
            responseCode = "200",
            description = "User found",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "User ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        var user = userService.getUserById(id);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @GetMapping
    @Operation(
            summary = "Get all users",
            description = "Retrieves a list of all users in the system"
    )
    @ApiResponse(
            responseCode = "200",
            description = "List of users",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        var users = userService.getAllUsers()
                .stream()
                .map(userMapper::toResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an existing user",
            description = "Updates user details. All fields are optional for partial updates."
    )
    @ApiResponse(
            responseCode = "200",
            description = "User updated successfully",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    public ResponseEntity<UserResponse> updateUser(
            @Parameter(description = "User ID (UUID)")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        var user = userService.updateUser(id, request);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a user",
            description = "Permanently deletes a user from the system"
    )
    @ApiResponse(responseCode = "204", description = "User deleted successfully")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "User ID (UUID)")
            @PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
