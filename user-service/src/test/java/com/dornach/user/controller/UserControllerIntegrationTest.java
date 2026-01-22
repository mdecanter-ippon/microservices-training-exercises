package com.dornach.user.controller;

import com.dornach.user.domain.User;
import com.dornach.user.domain.UserRole;
import com.dornach.user.dto.CreateUserRequest;
import com.dornach.user.dto.UpdateUserRequest;
import com.dornach.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /users - should create user and return 201")
    void createUser_Success() throws Exception {
        var request = new CreateUserRequest(
                "alice@dornach.com",
                "Alice",
                "Martin",
                UserRole.EMPLOYEE
        );

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alice@dornach.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Martin"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /users - should return 400 for invalid email")
    void createUser_InvalidEmail() throws Exception {
        var request = new CreateUserRequest(
                "invalid-email",
                "Alice",
                "Martin",
                UserRole.EMPLOYEE
        );

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /users - should return 409 for duplicate email")
    void createUser_DuplicateEmail() throws Exception {
        userRepository.save(new User("alice@dornach.com", "Alice", "Martin", UserRole.EMPLOYEE));

        var request = new CreateUserRequest(
                "alice@dornach.com",
                "Bob",
                "Dupont",
                UserRole.MANAGER
        );

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email Already Exists"))
                .andExpect(jsonPath("$.email").value("alice@dornach.com"));
    }

    @Test
    @DisplayName("GET /users/{id} - should return user")
    void getUserById_Success() throws Exception {
        var user = userRepository.save(new User("alice@dornach.com", "Alice", "Martin", UserRole.ADMIN));

        mockMvc.perform(get("/users/{id}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("alice@dornach.com"));
    }

    @Test
    @DisplayName("GET /users/{id} - should return 404 for unknown user")
    void getUserById_NotFound() throws Exception {
        mockMvc.perform(get("/users/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User Not Found"));
    }

    @Test
    @DisplayName("GET /users - should return all users")
    void getAllUsers() throws Exception {
        userRepository.save(new User("alice@dornach.com", "Alice", "Martin", UserRole.EMPLOYEE));
        userRepository.save(new User("bob@dornach.com", "Bob", "Dupont", UserRole.MANAGER));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /users/{id} - should update user")
    void updateUser_Success() throws Exception {
        var user = userRepository.save(new User("alice@dornach.com", "Alice", "Martin", UserRole.EMPLOYEE));

        var request = new UpdateUserRequest(null, "Alicia", null, UserRole.MANAGER, null);

        mockMvc.perform(put("/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Alicia"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @DisplayName("DELETE /users/{id} - should delete user")
    void deleteUser_Success() throws Exception {
        var user = userRepository.save(new User("alice@dornach.com", "Alice", "Martin", UserRole.EMPLOYEE));

        mockMvc.perform(delete("/users/{id}", user.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/{id}", user.getId()))
                .andExpect(status().isNotFound());
    }
}
