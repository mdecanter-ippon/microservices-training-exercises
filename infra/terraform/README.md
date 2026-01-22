# Terraform API Gateway Configuration

This Terraform configuration creates an AWS API Gateway v2 (HTTP API) that routes requests to the microservices in the Dornach system.

## Prerequisites

- Terraform v1.0.0+
- Docker with LocalStack running (as configured in docker-compose.yml)
- AWS CLI (optional, for testing)

## Configuration Files

- `providers.tf` - AWS provider configuration for LocalStack
- `main.tf` - Main API Gateway resources
- `variables.tf` - Configurable variables
- `outputs.tf` - Output values

## Usage

### Initialize Terraform

```bash
cd infra/terraform
terraform init
```

### Review the execution plan

```bash
terraform plan
```

### Apply the configuration

```bash
terraform apply
```

### Destroy the infrastructure (when needed)

```bash
terraform destroy
```

## Architecture

This configuration creates:

1. **API Gateway v2 (HTTP API)** - Main gateway endpoint
2. **Three HTTP Proxy Integrations** - One for each microservice:
   - User Service: `http://user-service:8081/{proxy}`
   - Shipment Service: `http://shipment-service:8082/{proxy}`
   - Order Service: `http://order-service:8083/{proxy}`
3. **Three Routes** - Route requests to appropriate services:
   - `ANY /users/{proxy+}` → User Service
   - `ANY /shipments/{proxy+}` → Shipment Service
   - `ANY /orders/{proxy+}` → Order Service
4. **Production Stage** - Deployed stage named "prod"
5. **CloudWatch Logs** - For API Gateway access logging

## Customization

You can customize the configuration by modifying `variables.tf` or creating a `terraform.tfvars` file with your preferred values.

## Notes

- The configuration is designed to work with LocalStack running in Docker
- All service URLs use Docker service names for internal networking
- CORS is configured to allow requests from any origin by default
- Access logging is enabled to CloudWatch (simulated by LocalStack)
