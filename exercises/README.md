# Training Exercises

This folder contains all exercise instructions for the microservices training.

## Exercise List

| Step | File | Topics |
|------|------|--------|
| 1 | [STEP_1_REST_FUNDAMENTALS.md](STEP_1_REST_FUNDAMENTALS.md) | Java Records, DTOs, RFC 7807, Virtual Threads |
| 2 | [STEP_2_SERVICE_COMMUNICATION.md](STEP_2_SERVICE_COMMUNICATION.md) | RestClient, Resilience4j, Retry/Timeout |
| 3 | [STEP_3_CONTRACT_FIRST.md](STEP_3_CONTRACT_FIRST.md) | springdoc-openapi, Swagger UI, Client generation |
| 4 | [STEP_4_API_GATEWAY.md](STEP_4_API_GATEWAY.md) | LocalStack, HTTP API v2, Rate limiting |
| 5 | [STEP_5_H2M_AUTHENTICATION.md](STEP_5_H2M_AUTHENTICATION.md) | Keycloak, JWT, OAuth2/OIDC |
| 6 | [STEP_6_M2M_AUTHENTICATION.md](STEP_6_M2M_AUTHENTICATION.md) | Client Credentials, Service accounts, RBAC |
| 7 | [STEP_7_DISTRIBUTED_TRACING.md](STEP_7_DISTRIBUTED_TRACING.md) | Micrometer, Zipkin, Log correlation |
| Bonus A | [BONUS_A_TESTCONTAINERS.md](BONUS_A_TESTCONTAINERS.md) | PostgreSQL + Keycloak containers |
| Bonus B | [BONUS_B_ASYNC_SQS.md](BONUS_B_ASYNC_SQS.md) | Spring Cloud AWS, DLQ |
| Bonus C | [BONUS_C_MAPSTRUCT.md](BONUS_C_MAPSTRUCT.md) | MapStruct, DTO mapping |

## Getting Started

Start with **Step 1**: Open [STEP_1_REST_FUNDAMENTALS.md](STEP_1_REST_FUNDAMENTALS.md) and follow the instructions.

## If You Get Stuck

Each step has a checkpoint branch you can use to catch up:

```bash
# Save your work and switch to a checkpoint
git stash && git checkout step-1-complete
```

Available checkpoints:
- `step-1-complete` through `step-7-complete`
- `bonus-a-complete`, `bonus-b-complete`, `bonus-c-complete`
