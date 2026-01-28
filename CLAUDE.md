# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot 3.4 microservices training project for teaching cloud-native development patterns. It contains three services that form a simple e-commerce domain:

- **user-service** (port 8081): User management
- **shipment-service** (port 8082): Shipment tracking
- **order-service** (port 8083): Order orchestration (calls user-service and shipment-service)

The exercises progressively build from REST fundamentals through service communication, authentication, and distributed tracing.

## Build and Run Commands

```bash
# Build all services
mvn clean compile

# Build without tests
mvn clean package -DskipTests

# Run a specific service
mvn spring-boot:run -pl user-service
mvn spring-boot:run -pl shipment-service
mvn spring-boot:run -pl order-service

# Run tests for a specific service
mvn test -pl user-service
```

## Infrastructure (Docker Compose)

```bash
# Start PostgreSQL only (local development uses H2 by default)
docker-compose up -d postgres

# Start LocalStack (for API Gateway exercises, Step 4+)
docker-compose up -d localstack

# Start Keycloak (for authentication exercises, Step 5+)
docker-compose up -d keycloak

# Start Zipkin (for distributed tracing, Step 7)
docker-compose up -d zipkin

# Start all infrastructure
docker-compose up -d
```

Infrastructure ports:
- PostgreSQL: 5432 (user: dornach, password: dornach)
- Keycloak: 8080 (admin: admin/admin)
- LocalStack: 4566
- Zipkin: 9411

## Architecture

```
┌────────────────────┐
│   API Gateway      │  (LocalStack, Step 4+)
│   :4566            │
└─────────┬──────────┘
          │
    ┌─────┴─────┐
    │           │
    ▼           ▼
┌─────────┐   ┌─────────┐
│  order  │──▶│  user   │   order-service calls user-service
│ service │   │ service │   and shipment-service via RestClient
│  :8083  │   │  :8081  │
└────┬────┘   └─────────┘
     │
     ▼
┌─────────┐
│shipment │
│ service │
│  :8082  │
└─────────┘
```

## Key Technical Details

- **Java 21** with Spring Boot 3.4.1
- **H2 in-memory database** for local development (auto-configured)
- **PostgreSQL** for Docker profile (`-Dspring.profiles.active=docker`)
- **springdoc-openapi** for API documentation (Swagger UI at `/swagger-ui.html`)
- **Resilience4j** for retry/timeout patterns in order-service
- **RestClient** for service-to-service communication (not WebClient/Feign)

## Code Conventions

Each service follows the standard Spring Boot layered architecture:
- `controller/` - REST endpoints
- `service/` - Business logic
- `repository/` - Data access (Spring Data JPA)
- `domain/` - JPA entities
- `dto/` - Request/Response records (Java records for DTOs)
- `exception/` - Custom exceptions and handlers
- `client/` - External service clients (order-service only)

## Exercise Progression

The project is structured for step-by-step learning with checkpoint branches:
- `step-1-complete` through `step-7-complete`
- `bonus-a-complete`, `bonus-b-complete`, `bonus-c-complete`

Use `git checkout step-N-complete` to catch up to a specific step.

## API Testing

Bruno collections are in `/bruno` folder. Select "Direct" environment for local service testing. After Step 4, update the `api_id` environment variable for gateway tests.

## Infrastructure Scripts

Located in `/infra`:
- `setup-gateway.sh` - Creates API Gateway with path-based routing
- `setup-keycloak.sh` - Configures Keycloak realm, clients, and users
- `test-gateway.sh` / `cleanup-gateway.sh` - Gateway testing utilities
