package com.dornach.user.controller;

import com.dornach.user.dto.CreateUserRequest;
import com.dornach.user.dto.UserResponse;
import com.dornach.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for User management.
 *
 * TODO (Step 1 - Exercise 3): Implement the following endpoints:
 *
 * | Method | Path         | Description      | Response Code |
 * |--------|--------------|------------------|---------------|
 * | POST   | /users       | Create a user    | 201 Created   |
 * | GET    | /users       | List all users   | 200 OK        |
 * | GET    | /users/{id}  | Get user by ID   | 200 OK / 404  |
 * | PUT    | /users/{id}  | Update user      | 200 OK / 404  |
 * | DELETE | /users/{id}  | Delete user      | 204 No Content|
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers().stream()
                .map(UserResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        // TODO (Step 1): Return 404 if user not found
        var user = userService.getUserById(id);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        // TODO (Step 1):
        // 1. Add @Valid annotation to enable validation
        // 2. Return 201 Created with Location header
        // Hint: Use ServletUriComponentsBuilder for the Location header

        var user = userService.createUser(request);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @RequestBody CreateUserRequest request) {
        var user = userService.updateUser(id, request);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
