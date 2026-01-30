# Step 8: Deploying to AWS Lambda

---

## Recap: Step 7

In Step 7, you implemented **distributed tracing**:
- **Zipkin** running as trace collector at `localhost:9411`
- **Micrometer Tracing** with OpenTelemetry bridge
- **Trace ID** shared across all services in a request
- **Log correlation** with `[serviceName,traceId,spanId]` pattern

Now your microservices are observable. But they're still running on Tomcat - **always on**, consuming resources even when idle. Let's explore **serverless** with AWS Lambda.

---

## Objectives

By the end of this exercise, you will:
- Understand when serverless makes sense (and when it doesn't)
- Create an Order Validation Lambda that integrates with user-service
- Deploy to LocalStack and experience cold start firsthand
- Expose your Lambda via API Gateway

---

## Prerequisites

- Step 7 completed (distributed tracing working)
- **Docker Desktop** running
- **LocalStack** running with Lambda support
- **AWS CLI** installed (for `awslocal` commands)

---

## Context: Why Serverless?

### The Use Case: Order Validation

Before creating an order, we want to validate:
- Does the user exist?
- Is the quantity valid (> 0)?
- Is the total price valid (> 0)?

**Why a Lambda for this?**

| Approach | Pros | Cons |
|----------|------|------|
| Add validation to order-service | Simple, no new service | order-service must be running 24/7 |
| Dedicated validation-service | Separation of concerns | Another service to maintain 24/7 |
| **Lambda** | Runs only when needed, auto-scales | Cold start latency |

A validation endpoint is a perfect Lambda use case:
- **Stateless** - No database needed
- **Short-lived** - Quick validation, return result
- **Variable load** - Might have bursts of orders, then nothing

### Traditional vs Serverless

```
Traditional (order-service on Tomcat):
├── Server starts → consumes memory/CPU
├── Waits for requests... (still consuming)
├── Validates order
└── Waits again... (still consuming)

Serverless (validation Lambda):
├── Request arrives → AWS creates environment
├── Validates order
├── Response sent → environment may be destroyed
└── No request = $0
```

---

## Exercise 1: Create the Validation Function (Local)

Before deploying to Lambda, let's create and test the function **locally**.

### 1.1 Create the Module Structure

```bash
mkdir -p lambda-service/src/main/java/com/dornach/lambda
mkdir -p lambda-service/src/main/resources
```

### 1.2 Create the Validation Function

**File:** `lambda-service/src/main/java/com/dornach/lambda/OrderValidationFunction.java`

```java
package com.dornach.lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class OrderValidationFunction implements Function<OrderValidationRequest, OrderValidationResponse> {

    private static final Logger log = LoggerFactory.getLogger(OrderValidationFunction.class);
    private final RestClient userServiceClient;

    public OrderValidationFunction(
            @Value("${user.service.url:http://localhost:8081}") String userServiceUrl) {
        this.userServiceClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    @Override
    public OrderValidationResponse apply(OrderValidationRequest request) {
        log.info("Validating order for user: {}", request.userId());

        List<String> errors = new ArrayList<>();

        // Validate quantity
        if (request.quantity() == null || request.quantity() <= 0) {
            errors.add("Quantity must be greater than 0");
        }

        // Validate total price
        if (request.totalPrice() == null || request.totalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Total price must be greater than 0");
        }

        // Validate user exists (call user-service)
        if (request.userId() != null) {
            try {
                var response = userServiceClient.get()
                        .uri("/users/{id}", request.userId())
                        .retrieve()
                        .toBodilessEntity();

                log.info("User {} found", request.userId());
            } catch (RestClientException e) {
                log.warn("User {} not found: {}", request.userId(), e.getMessage());
                errors.add("User not found: " + request.userId());
            }
        } else {
            errors.add("User ID is required");
        }

        boolean valid = errors.isEmpty();
        log.info("Validation result: valid={}, errors={}", valid, errors);

        return new OrderValidationResponse(valid, errors);
    }
}
```

### 1.3 Create Request/Response Records

**File:** `lambda-service/src/main/java/com/dornach/lambda/OrderValidationRequest.java`

```java
package com.dornach.lambda;

import java.math.BigDecimal;

public record OrderValidationRequest(
    String userId,
    String productName,
    Integer quantity,
    BigDecimal totalPrice
) {}
```

**File:** `lambda-service/src/main/java/com/dornach/lambda/OrderValidationResponse.java`

```java
package com.dornach.lambda;

import java.util.List;

public record OrderValidationResponse(
    boolean valid,
    List<String> errors
) {}
```

### 1.4 Create the Application Class

**File:** `lambda-service/src/main/java/com/dornach/lambda/LambdaApplication.java`

```java
package com.dornach.lambda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LambdaApplication {
    public static void main(String[] args) {
        SpringApplication.run(LambdaApplication.class, args);
    }
}
```

### 1.5 Create the POM (Web Mode for Local Testing)

**File:** `lambda-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.dornach</groupId>
        <artifactId>microservices-training</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>lambda-service</artifactId>
    <name>Lambda Service</name>

    <properties>
        <spring-cloud-function.version>4.1.3</spring-cloud-function.version>
    </properties>

    <dependencies>
        <!-- Spring Cloud Function Web (for local testing) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-function-web</artifactId>
            <version>${spring-cloud-function.version}</version>
        </dependency>
    </dependencies>
</project>
```

### 1.6 Create Configuration

**File:** `lambda-service/src/main/resources/application.yaml`

```yaml
server:
  port: 8085

spring:
  application:
    name: lambda-service

user:
  service:
    url: ${USER_SERVICE_URL:http://localhost:8081}
```

### 1.7 Add Module to Parent POM

**File:** `pom.xml` (root) - Add to `<modules>`:

```xml
<module>lambda-service</module>
```

### 1.8 Test Locally

Start user-service first (if not running):
```bash
cd user-service && mvn spring-boot:run
```

In another terminal, start lambda-service:
```bash
cd lambda-service && mvn spring-boot:run
```

Test the validation:

```bash
# Valid order (user exists)
curl -X POST http://localhost:8085/orderValidationFunction \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 2,
    "totalPrice": 1999.99
  }'
```

**Expected response:**
```json
{"valid":true,"errors":[]}
```

```bash
# Invalid order (user doesn't exist, invalid quantity)
curl -X POST http://localhost:8085/orderValidationFunction \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "99999999-9999-9999-9999-999999999999",
    "productName": "Laptop",
    "quantity": -1,
    "totalPrice": 100
  }'
```

**Expected response:**
```json
{"valid":false,"errors":["Quantity must be greater than 0","User not found: 99999999-9999-9999-9999-999999999999"]}
```

<details>
<summary>💡 How does Spring Cloud Function expose the endpoint?</summary>

Spring Cloud Function automatically:
- Detects beans implementing `Function<I, O>`
- Exposes them as HTTP endpoints
- Uses the bean name as the URL path (`orderValidationFunction`)

This works locally for testing, and the same code runs on Lambda!

</details>

---

## Exercise 2: Adapt for Lambda Deployment

Now that validation works locally, let's adapt it for AWS Lambda.

### 2.1 Update Dependencies

Replace the web starter with the AWS adapter in `lambda-service/pom.xml`:

```xml
<dependencies>
    <!-- REMOVE: spring-cloud-starter-function-web -->

    <!-- ADD: AWS Lambda adapter -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-function-adapter-aws</artifactId>
        <version>${spring-cloud-function.version}</version>
    </dependency>

    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-core</artifactId>
        <version>1.2.3</version>
    </dependency>

    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-events</artifactId>
        <version>3.14.0</version>
    </dependency>

    <!-- Keep web starter for RestClient -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### 2.2 Add the Shade Plugin

Lambda requires a single JAR with all dependencies. Add to `lambda-service/pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.1</version>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <shadedArtifactAttached>true</shadedArtifactAttached>
                <shadedClassifierName>aws</shadedClassifierName>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                        <resource>META-INF/spring.handlers</resource>
                    </transformer>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                        <resource>META-INF/spring.schemas</resource>
                    </transformer>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                        <resource>META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports</resource>
                    </transformer>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
            </configuration>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2.3 Update Configuration for Lambda

**File:** `lambda-service/src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: lambda-service
  main:
    web-application-type: none  # No Tomcat needed in Lambda
  cloud:
    function:
      definition: orderValidationFunction

user:
  service:
    url: ${USER_SERVICE_URL:http://host.docker.internal:8081}
```

**Note:** We use `host.docker.internal` because Lambda runs inside Docker (LocalStack), and needs to reach user-service on your host machine.

### 2.4 Build the Shaded JAR

```bash
cd lambda-service
mvn clean package -DskipTests
```

Verify you have two JARs:
```bash
ls -lh target/*.jar
```

| File | Size | Purpose |
|------|------|---------|
| `lambda-service-1.0.0-SNAPSHOT.jar` | ~2MB | Regular JAR |
| `lambda-service-1.0.0-SNAPSHOT-aws.jar` | ~30MB | **Lambda deployment** |

---

## Exercise 3: Deploy to LocalStack

### 3.1 Ensure LocalStack Has Lambda Support

Verify your `docker-compose.yml` includes Lambda:

```yaml
localstack:
  environment:
    - SERVICES=apigateway,apigatewayv2,lambda,iam,logs,s3
    - LAMBDA_EXECUTOR=docker
    - DOCKER_HOST=unix:///var/run/docker.sock
  volumes:
    - "/var/run/docker.sock:/var/run/docker.sock"
```

Restart LocalStack if you made changes:
```bash
docker-compose up -d localstack
```

### 3.2 Deploy Using the Provided Script

**Linux/macOS:**
```bash
./infra/deploy-lambda.sh
```

**Windows PowerShell:**
```powershell
.\infra\windows\deploy-lambda.ps1
```

### 3.3 Understanding the Deployment

The script executes these key commands:

```bash
# Create Lambda function
awslocal lambda create-function \
    --function-name order-validation \
    --runtime java21 \
    --handler org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest \
    --zip-file fileb://lambda-service/target/lambda-service-1.0.0-SNAPSHOT-aws.jar \
    --timeout 30 \
    --memory-size 512
```

| Parameter | Value | Why |
|-----------|-------|-----|
| `--runtime` | java21 | Java version |
| `--handler` | FunctionInvoker::handleRequest | Spring Cloud Function adapter |
| `--timeout` | 30 | Max execution time (cold start can take 10s+) |
| `--memory-size` | 512 | More memory = more CPU allocated |

### 3.4 Test the Lambda Directly

```bash
# Test with valid order
awslocal lambda invoke \
    --function-name order-validation \
    --payload '{"userId":"11111111-1111-1111-1111-111111111111","productName":"Laptop","quantity":2,"totalPrice":1999.99}' \
    --cli-binary-format raw-in-base64-out \
    response.json

cat response.json
```

### 3.5 Experience Cold Start

```bash
# First call - SLOW (cold start: JVM + Spring startup)
time awslocal lambda invoke --function-name order-validation \
    --payload '{"userId":"11111111-1111-1111-1111-111111111111","quantity":1,"totalPrice":100}' \
    --cli-binary-format raw-in-base64-out /dev/null

# Second call immediately - FAST (container reused)
time awslocal lambda invoke --function-name order-validation \
    --payload '{"userId":"11111111-1111-1111-1111-111111111111","quantity":1,"totalPrice":100}' \
    --cli-binary-format raw-in-base64-out /dev/null
```

**Observe:** First call takes several seconds, second call is much faster.

---

## Exercise 4: Expose via API Gateway

### 4.1 Run the Gateway Setup Script

**Linux/macOS:**
```bash
./infra/setup-lambda-gateway.sh
```

**Windows PowerShell:**
```powershell
.\infra\windows\setup-lambda-gateway.ps1
```

### 4.2 Understanding the API Gateway Flow

```
┌──────────┐     ┌─────────────┐     ┌────────────────┐     ┌──────────────┐
│  Client  │────▶│ API Gateway │────▶│ Lambda         │────▶│ user-service │
│  (curl)  │     │ POST /validate│   │ (validation)   │     │ (check user) │
└──────────┘     └─────────────┘     └────────────────┘     └──────────────┘
                       │                     │
                       │    HTTP 200         │
                       │◀────────────────────│
                       │  {valid: true/false}│
```

The script creates:
1. **HTTP API** (API Gateway v2) - Faster and cheaper than REST API
2. **Lambda Integration** - Routes requests to your function
3. **Route** - Maps `POST /validate` to the Lambda
4. **Auto-deploy Stage** - Changes deploy automatically

### 4.3 Test via HTTP

```bash
# Get the API ID
API_ID=$(awslocal apigatewayv2 get-apis --query "Items[?Name=='dornach-lambda-api'].ApiId" --output text)

# Test valid order
curl -X POST "http://localhost:4566/restapis/$API_ID/\$default/_user_request_/validate" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "11111111-1111-1111-1111-111111111111",
    "productName": "Laptop",
    "quantity": 2,
    "totalPrice": 1999.99
  }'

# Test invalid order
curl -X POST "http://localhost:4566/restapis/$API_ID/\$default/_user_request_/validate" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "invalid-user",
    "quantity": -1,
    "totalPrice": 0
  }'
```

---

## Exercise 5: Test Error Scenarios

### 5.1 What Happens When user-service is Down?

Stop user-service (Ctrl+C), then call the Lambda:

```bash
awslocal lambda invoke \
    --function-name order-validation \
    --payload '{"userId":"11111111-1111-1111-1111-111111111111","quantity":1,"totalPrice":100}' \
    --cli-binary-format raw-in-base64-out \
    response.json

cat response.json
```

**Expected:** `{"valid":false,"errors":["User not found: ..."]}`

The Lambda handles the error gracefully - no crash!

### 5.2 Check CloudWatch Logs

```bash
awslocal logs filter-log-events \
    --log-group-name "/aws/lambda/order-validation" \
    --limit 10
```

### 5.3 Restart user-service

```bash
cd user-service && mvn spring-boot:run
```

---

## Troubleshooting

### "Function not found"

```bash
awslocal lambda list-functions
```
If empty, run `./infra/deploy-lambda.sh` again.

### "Connection refused" to user-service

Lambda runs in Docker. Use `host.docker.internal`:
```yaml
user.service.url: http://host.docker.internal:8081
```

### Cold start timeout (> 30s)

Increase timeout and memory:
```bash
awslocal lambda update-function-configuration \
    --function-name order-validation \
    --timeout 60 \
    --memory-size 1024
```

---

## Validation Checklist

Before completing Step 8:

- [ ] Validation function works locally (Exercise 1)
- [ ] Shaded JAR built successfully (~30MB)
- [ ] Lambda deployed to LocalStack
- [ ] Cold start observed (slow first call, fast second)
- [ ] API Gateway routes to Lambda
- [ ] Valid orders return `{"valid": true}`
- [ ] Invalid orders return errors list
- [ ] Graceful handling when user-service is down

---

## Summary

In this exercise, you learned:

| Concept | What You Did |
|---------|--------------|
| **Use Case** | Order validation - perfect for serverless (stateless, short-lived) |
| **Spring Cloud Function** | Created portable `Function<I,O>` bean |
| **Local Testing** | Tested as REST endpoint before Lambda deployment |
| **Shaded JAR** | Built all-in-one JAR with Maven Shade |
| **Cold Start** | Experienced JVM startup delay firsthand |
| **API Gateway** | Exposed Lambda via HTTP endpoint |
| **Error Handling** | Graceful degradation when dependencies fail |

---

## Challenge: Lambda + SQS (Optional)

If you completed **Bonus B (Async SQS)**, try this challenge:

**Replace notification-service with a Lambda that consumes SQS messages.**

Hints:
1. Add `spring-cloud-function-adapter-aws` to notification-service
2. Change `@SqsListener` to a `Function<SQSEvent, Void>` bean
3. Configure the Lambda with an SQS trigger instead of API Gateway

This combines async messaging with serverless - a powerful pattern for event processing!

---

## Before Moving On

**Option A:** You completed all exercises
```bash
git add . && git commit -m "Complete Step 8 - Lambda deployment"
```

**Option B:** You need to catch up
```bash
git stash && git checkout step-8-complete
```

**Next:** Explore the [Bonus Exercises](./README.md) for Testcontainers, Async SQS, and MapStruct.
