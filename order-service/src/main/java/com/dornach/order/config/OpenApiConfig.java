package com.dornach.order.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Dornach Order Service API",
                version = "1.0.0",
                description = """
                        Order orchestration service for the Dornach platform.

                        This service provides:
                        - Order creation and management
                        - Order-to-Shipment orchestration
                        - Integration with user and shipment services

                        ## Architecture Note
                        This service acts as an orchestrator, coordinating between user-service
                        and shipment-service using synchronous REST calls with Resilience4j for fault tolerance.
                        """,
                contact = @Contact(
                        name = "Dornach Engineering Team",
                        email = "engineering@dornach.com"
                ),
                license = @License(name = "Apache 2.0")
        ),
        servers = {
                @Server(url = "http://localhost:8083", description = "Local Development"),
                @Server(url = "https://api.dornach.com", description = "Production")
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
