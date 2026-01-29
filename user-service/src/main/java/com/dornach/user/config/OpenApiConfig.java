package com.dornach.user.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAPI 3.1 configuration for the User Service.
 * This serves as the single source of truth for API contracts.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Dornach User Service API",
                version = "1.0.0",
                description = """
                        Identity and user management service for the Dornach platform.

                        This service provides:
                        - User CRUD operations
                        - Role-based access control (RBAC)
                        - User status management

                        ## Authentication
                        All endpoints (except health checks) require a valid JWT token obtained from Keycloak.
                        """,
                contact = @Contact(
                        name = "Dornach Engineering Team",
                        email = "engineering@dornach.com",
                        url = "https://dornach.com"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8081", description = "Local Development"),
                @Server(url = "http://localhost:4566", description = "API Gateway (LocalStack)"),
                @Server(url = "https://api.dornach.com", description = "Production")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT token obtained from Keycloak. Format: `Bearer <token>`"
)
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer customizeOpenApi() {
        return openApi -> {
            // Add global error responses
            openApi.getComponents()
                    .addResponses("BadRequest", createErrorResponse(
                            "Bad Request",
                            "The request is invalid or malformed"
                    ))
                    .addResponses("Unauthorized", createErrorResponse(
                            "Unauthorized",
                            "Authentication is required"
                    ))
                    .addResponses("Forbidden", createErrorResponse(
                            "Forbidden",
                            "You don't have permission to access this resource"
                    ))
                    .addResponses("NotFound", createErrorResponse(
                            "Not Found",
                            "The requested resource was not found"
                    ))
                    .addResponses("Conflict", createErrorResponse(
                            "Conflict",
                            "The request conflicts with the current state (e.g., duplicate email)"
                    ))
                    .addResponses("InternalServerError", createErrorResponse(
                            "Internal Server Error",
                            "An unexpected error occurred"
                    ));
        };
    }

    private ApiResponse createErrorResponse(String title, String description) {
        Map<String, Object> errorExample = new LinkedHashMap<>();
        errorExample.put("type", "https://api.dornach.com/errors/error-type");
        errorExample.put("title", title);
        errorExample.put("status", 400);
        errorExample.put("detail", "Detailed error message");
        errorExample.put("timestamp", "2024-01-15T10:30:00Z");

        Example example = new Example();
        example.setValue(errorExample);

        MediaType mediaType = new MediaType();
        mediaType.addExamples("default", example);

        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/problem+json", mediaType));
    }
}
