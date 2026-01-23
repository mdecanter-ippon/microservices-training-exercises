package com.dornach.user;

import com.dornach.user.domain.UserRole;
import com.dornach.user.dto.CreateUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests using Testcontainers with real PostgreSQL and Keycloak instances.
 * These tests validate the complete authentication flow with real JWT tokens.
 *
 * <p>These tests require Docker to be running. They will be skipped automatically
 * if Docker is not available.</p>
 *
 * <p>Run with: {@code mvn test -Dtest=UserServiceContainerTest}</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("docker")
@Testcontainers(disabledWithoutDocker = true)
class UserServiceContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("userdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
            .withRealmImportFile("dornach-realm.json");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Keycloak configuration (PostgreSQL is auto-configured via @ServiceConnection)
        String issuerUri = keycloak.getAuthServerUrl() + "/realms/dornach";
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> issuerUri + "/protocol/openid-connect/certs");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Disable data.sql initialization - tests should start with empty database
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /users without token should return 401")
    void getUsersWithoutToken_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users with valid Alice token should return 200")
    void getUsersWithValidToken_ShouldReturn200() throws Exception {
        String token = getAccessToken("alice", "alice123");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /users with valid Bob (admin) token should create user")
    void createUserWithAdminToken_ShouldReturn201() throws Exception {
        String token = getAccessToken("bob", "bob123");

        var request = new CreateUserRequest(
                "newuser@dornach.com",
                "New",
                "User",
                UserRole.EMPLOYEE
        );

        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@dornach.com"))
                .andExpect(jsonPath("$.firstName").value("New"));
    }

    @Test
    @DisplayName("Full flow: create and list users with authentication")
    void fullAuthenticatedFlow() throws Exception {
        String adminToken = getAccessToken("bob", "bob123");

        // Create first user
        var request1 = new CreateUserRequest("user1@dornach.com", "User", "One", UserRole.EMPLOYEE);
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Create second user
        var request2 = new CreateUserRequest("user2@dornach.com", "User", "Two", UserRole.MANAGER);
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // List users with Alice's token (user role)
        String userToken = getAccessToken("alice", "alice123");
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    /**
     * Obtains an access token from Keycloak using the Resource Owner Password Credentials flow.
     * This is suitable for testing purposes only.
     */
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

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get token: " + response.body());
        }

        // Extract access_token from JSON response
        var jsonNode = objectMapper.readTree(response.body());
        return jsonNode.get("access_token").asText();
    }
}
