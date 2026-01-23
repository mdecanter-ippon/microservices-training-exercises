# OpenAPI Specifications

This directory contains the OpenAPI 3.1 specifications for all Dornach microservices.

## Available Specifications

- `user-service.yaml` - Identity and user management API
- `shipment-service.yaml` - Shipment tracking and management API
- `order-service.yaml` - Order orchestration API

## Generating Specifications

To export the latest OpenAPI specs from running services:

```bash
cd scripts
./export-openapi-specs.sh
```

This will:
1. Connect to each running service
2. Fetch the OpenAPI spec from `/v3/api-docs.yaml`
3. Save it to this directory

## Viewing the Documentation

### Option 1: Swagger UI (Interactive)

Each service exposes Swagger UI at `/swagger-ui.html`:

- User Service: http://localhost:8081/swagger-ui.html
- Shipment Service: http://localhost:8082/swagger-ui.html
- Order Service: http://localhost:8083/swagger-ui.html

### Option 2: Redoc (Clean Documentation)

You can use Redoc to render beautiful documentation:

```bash
npx @redocly/cli preview-docs openapi/user-service.yaml
```

### Option 3: VS Code Extension

Install the "OpenAPI (Swagger) Editor" extension and open any YAML file.

## Generating Client SDKs

To generate type-safe client libraries:

```bash
cd scripts
./generate-clients.sh
```

Supported languages:
- Java (Spring RestClient)
- TypeScript (Axios)
- Python (requests)
- Go (net/http)
- And 50+ more

## Contract-First Development

### The Three Benefits

1. **Type Safety**
   - Generated clients catch breaking changes at compile time
   - No more runtime surprises

2. **Developer Velocity**
   - Auto-complete in your IDE
   - No manual `fetch()` or `RestClient` calls

3. **Living Documentation**
   - The spec IS the documentation
   - Always in sync with the code

### Example: Using Generated Client

```java
// Without generated client (manual)
RestClient client = RestClient.create();
UserResponse user = client.get()
    .uri("http://localhost:8081/users/{id}", userId)
    .retrieve()
    .body(UserResponse.class);

// With generated client (type-safe)
UserServiceApi api = new UserServiceApi(apiClient);
UserResponse user = api.getUserById(userId);
```

### Breaking Change Detection

```typescript
// Before: API returns { firstName, lastName }
const user = await usersApi.getUserById(id);
console.log(user.firstName); // ✓ Works

// After: API changes to { fullName }
const user = await usersApi.getUserById(id);
console.log(user.firstName); // ✗ Compile error!
```

## Best Practices

1. **Version your APIs**: Use `/v1/users`, `/v2/users` for breaking changes
2. **Add examples**: Help developers understand expected inputs/outputs
3. **Document errors**: Specify all possible error responses
4. **Use schemas**: Define reusable components for consistency

## Tools

- [OpenAPI Generator](https://openapi-generator.tech/)
- [Redocly CLI](https://redocly.com/docs/cli/)
- [Swagger Editor](https://editor.swagger.io/)
- [Postman](https://www.postman.com/) - Can import OpenAPI specs directly
