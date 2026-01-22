# Microservices Training - Starter Project

Welcome to the Dornach Microservices Training! This repository contains the starter code for hands-on exercises.

## Prerequisites

Before starting, make sure you have:

- **Java 21** installed
- **Maven 3.9+** installed
- **Docker** and **Docker Compose** installed
- **Bruno** installed (https://www.usebruno.com/downloads) - Required for API testing
- An IDE with Spring Boot support (IntelliJ IDEA, VS Code)

## Project Structure

```
microservices-training-starter/
├── exercises/                  # Exercise instructions (start here!)
│   ├── STEP_1_REST_FUNDAMENTALS.md
│   ├── STEP_2_SERVICE_COMMUNICATION.md
│   ├── STEP_3_CONTRACT_FIRST.md
│   ├── STEP_4_API_GATEWAY.md
│   ├── STEP_5_H2M_AUTHENTICATION.md
│   ├── STEP_6_M2M_AUTHENTICATION.md
│   ├── STEP_7_DISTRIBUTED_TRACING.md
│   ├── BONUS_A_TESTCONTAINERS.md
│   └── BONUS_B_ASYNC_SQS.md
├── user-service/               # User management service (port 8081)
├── order-service/              # Order management service (port 8083)
├── shipment-service/           # Shipment tracking service (port 8082)
├── bruno/                      # Bruno API collections for testing
├── infra/                      # Infrastructure scripts
└── docker-compose.yml          # Docker services (PostgreSQL, Keycloak, etc.)
```

## Getting Started

### 1. Clone and Build

```bash
# Clone the repository
git clone <repository-url>
cd microservices-training-starter

# Build all services
mvn clean compile
```

### 2. Start Infrastructure

```bash
# Start PostgreSQL (for later steps)
docker-compose up -d postgres

# For Step 4+: Start LocalStack
docker-compose up -d localstack

# For Step 5+: Start Keycloak
docker-compose up -d keycloak
```

### 3. Run a Service

```bash
# Run user-service (Step 1)
cd user-service
mvn spring-boot:run
```

### 4. Open Exercises

Start with **Step 1**: Open `exercises/STEP_1_REST_FUNDAMENTALS.md` and follow the instructions.

## Training Path

| Step | Topic | Duration |
|------|-------|----------|
| 1 | REST Fundamentals & Java 21 | 45-60 min |
| 2 | Service Communication | 45-60 min |
| 3 | Contract-First & OpenAPI | 30-45 min |
| 4 | API Gateway | 45-60 min |
| 5 | H2M Authentication | 60-75 min |
| 6 | M2M Authentication | 45-60 min |
| 7 | Distributed Tracing | 45-60 min |
| Bonus A | Testcontainers | 30-45 min |
| Bonus B | Async SQS | 45-60 min |

## If You Get Stuck

Each step has a checkpoint branch you can use to catch up:

```bash
# Example: catch up to Step 2
git stash
git checkout step-1-complete
```

Available checkpoints:
- `step-1-complete`
- `step-2-complete`
- `step-3-complete`
- `step-4-complete`
- `step-5-complete`
- `step-6-complete`
- `step-7-complete`
- `bonus-a-complete`
- `bonus-b-complete`

## Testing with Bruno

Bruno is **required** for this training. It provides API testing with assertions.

1. Open Bruno
2. Click "Open Collection"
3. Select the `bruno/` folder
4. Select environment: **Direct** (for local services)

## Useful Commands

```bash
# Build all services
mvn clean package -DskipTests

# Run a specific service
mvn spring-boot:run -pl user-service

# Run tests
mvn test -pl user-service

# Start all infrastructure
docker-compose up -d

# View logs
docker-compose logs -f keycloak
```

## Service Ports

| Service | Port | URL |
|---------|------|-----|
| user-service | 8081 | http://localhost:8081 |
| shipment-service | 8082 | http://localhost:8082 |
| order-service | 8083 | http://localhost:8083 |
| PostgreSQL | 5432 | - |
| Keycloak | 8080 | http://localhost:8080 |
| LocalStack | 4566 | http://localhost:4566 |
| Zipkin | 9411 | http://localhost:9411 |

## Questions?

Ask your instructor!
