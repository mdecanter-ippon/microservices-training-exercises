# LocalStack API Gateway v2 Setup Script for Dornach Microservices
# This script creates an HTTP API with path-based routing and rate limiting

$ErrorActionPreference = "Stop"

$LOCALSTACK_ENDPOINT = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { "http://localhost:4566" }

Write-Host "Setting up Dornach API Gateway with LocalStack..." -ForegroundColor Cyan
Write-Host ""

# Check if LocalStack is running
try {
    $healthCheck = Invoke-WebRequest -Uri "$LOCALSTACK_ENDPOINT/_localstack/health" -UseBasicParsing -ErrorAction SilentlyContinue
} catch {
    Write-Host "LocalStack is not running at $LOCALSTACK_ENDPOINT" -ForegroundColor Red
    Write-Host "   Start it with: docker-compose up -d localstack"
    exit 1
}

Write-Host "LocalStack is running" -ForegroundColor Green

# Set dummy AWS credentials for LocalStack
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = "us-east-1"

# Create HTTP API (v2 - faster and cheaper than REST API v1)
Write-Host ""
Write-Host "Creating HTTP API Gateway..." -ForegroundColor Yellow

$apiResult = aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-api `
    --name dornach-gateway `
    --protocol-type HTTP `
    --description "Dornach microservices API Gateway" `
    --query 'ApiId' --output text

$API_ID = $apiResult.Trim()
Write-Host "API created with ID: $API_ID" -ForegroundColor Green

# Create integrations for each service
Write-Host ""
Write-Host "Creating integrations for each service..." -ForegroundColor Yellow

# User Service Integration
$USER_INT = (aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type HTTP_PROXY `
    --integration-uri "http://user-service:8081/{proxy}" `
    --integration-method ANY `
    --payload-format-version 1.0 `
    --query 'IntegrationId' --output text).Trim()

Write-Host "  user-service integration: $USER_INT" -ForegroundColor White

# Shipment Service Integration
$SHIPMENT_INT = (aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type HTTP_PROXY `
    --integration-uri "http://shipment-service:8082/{proxy}" `
    --integration-method ANY `
    --payload-format-version 1.0 `
    --query 'IntegrationId' --output text).Trim()

Write-Host "  shipment-service integration: $SHIPMENT_INT" -ForegroundColor White

# Order Service Integration
$ORDER_INT = (aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type HTTP_PROXY `
    --integration-uri "http://order-service:8083/{proxy}" `
    --integration-method ANY `
    --payload-format-version 1.0 `
    --query 'IntegrationId' --output text).Trim()

Write-Host "  order-service integration: $ORDER_INT" -ForegroundColor White

# Create Routes (path-based routing)
Write-Host ""
Write-Host "Creating routes..." -ForegroundColor Yellow

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "ANY /users/{proxy+}" `
    --target "integrations/$USER_INT" | Out-Null

Write-Host "  /users/* -> user-service:8081" -ForegroundColor White

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "ANY /shipments/{proxy+}" `
    --target "integrations/$SHIPMENT_INT" | Out-Null

Write-Host "  /shipments/* -> shipment-service:8082" -ForegroundColor White

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "ANY /orders/{proxy+}" `
    --target "integrations/$ORDER_INT" | Out-Null

Write-Host "  /orders/* -> order-service:8083" -ForegroundColor White

# Create Stage with throttling
Write-Host ""
Write-Host "Creating production stage..." -ForegroundColor Yellow

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-stage `
    --api-id $API_ID `
    --stage-name prod `
    --auto-deploy | Out-Null

Write-Host "  Stage 'prod' created" -ForegroundColor White
Write-Host "  Note: Rate limiting configuration requires additional setup in LocalStack Pro" -ForegroundColor Gray

# Get the Gateway URL
$GATEWAY_URL = "$LOCALSTACK_ENDPOINT/restapis/$API_ID/prod/_user_request_"

Write-Host ""
Write-Host "API Gateway setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Gateway URL: $GATEWAY_URL" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test the gateway:" -ForegroundColor Cyan
Write-Host "  Invoke-WebRequest $GATEWAY_URL/users"
Write-Host "  Invoke-WebRequest $GATEWAY_URL/shipments"
Write-Host "  Invoke-WebRequest $GATEWAY_URL/orders"
Write-Host ""
Write-Host "Save API ID for cleanup: `$env:DORNACH_API_ID = '$API_ID'" -ForegroundColor Yellow
