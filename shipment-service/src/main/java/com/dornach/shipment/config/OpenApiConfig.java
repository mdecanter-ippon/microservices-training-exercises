package com.dornach.shipment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Dornach Shipment Service API",
                version = "1.0.0",
                description = """
                        Core business shipment management service for the Dornach platform.

                        This service provides:
                        - Shipment creation and tracking
                        - Status lifecycle management
                        - Integration with order management

                        ## Shipment Lifecycle
                        PENDING → SHIPPED → IN_TRANSIT → DELIVERED
                        """,
                contact = @Contact(
                        name = "Dornach Engineering Team",
                        email = "engineering@dornach.com"
                ),
                license = @License(name = "Apache 2.0")
        ),
        servers = {
                @Server(url = "http://localhost:8082", description = "Local Development"),
                @Server(url = "https://api.dornach.com", description = "Production")
        }
)
public class OpenApiConfig {
}
