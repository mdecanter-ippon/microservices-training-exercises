package com.dornach.notification.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Dornach Notification Service API",
                version = "1.0.0",
                description = """
                        Async notification service for the Dornach platform.

                        This service:
                        - Consumes order events from SQS queue
                        - Simulates sending notifications to a legacy system
                        - Demonstrates async messaging patterns with distributed tracing

                        Note: This service has no REST endpoints - it's a pure SQS consumer.
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
                @Server(url = "http://localhost:8084", description = "Local Development")
        }
)
public class OpenApiConfig {
}
