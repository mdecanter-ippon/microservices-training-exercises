# LocalStack API Gateway v2 Setup Script for Dornach Microservices (Windows PowerShell)
# This script creates an HTTP API with path-based routing and rate limiting

$ErrorActionPreference = "Stop"

$LOCALSTACK_ENDPOINT = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { "http://localhost:4566" }

Write-Host "Setting up Dornach API Gateway with LocalStack..." -ForegroundColor Cyan
Write-Host ""

# Check if LocalStack is running
try {
    $null = Invoke-RestMethod -Uri "$LOCALSTACK_ENDPOINT/_localstack/health" -Method Get -ErrorAction Stop
    Write-Host "[OK] LocalStack is running" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] LocalStack is not running at $LOCALSTACK_ENDPOINT" -ForegroundColor Red
    Write-Host "        Start it with: docker-compose up -d localstack"
    exit 1
}

# Create HTTP API (v2 - faster and cheaper than REST API v1)
Write-Host ""
Write-Host "Creating HTTP API Gateway..." -ForegroundColor Cyan

$API_ID = awslocal apigatewayv2 create-api `
    --name dornach-gateway `
    --protocol-type HTTP `
    --description "Dornach microservices API Gateway" `
    --query 'ApiId' --output text

Write-Host "[OK] API created with ID: $API_ID" -ForegroundColor Green

# Create integrations for each service
Write-Host ""
Write-Host "Creating integrations for each service..." -ForegroundColor Cyan

# User Service Integration
$USER_INT = awslocal apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type HTTP_PROXY `
    --integration-uri "http://user-service:8081/{proxy}" `
    --integration-method ANY `
    --payload-format-version 1.0 `
    --query 'IntegrationId' --output text

Write-Host "  [OK] user-service integration: $USER_INT" -ForegroundColor Green

# Shipment Service Integration
$SHIPMENT_INT = awslocal apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type HTTP_PROXY `
    --integration-uri "http://shipment-service:8082/{proxy}" `
    --integration-method ANY `
    --payload-format-version 1.0 `
    --query 'IntegrationId' --output text

Write-Host "  [OK] shipment-service integration: $SHIPMENT_INT" -ForegroundColor Green

# Order Service Integration
$ORDER_INT = awslocal apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type HTTP_PROXY `
    --integration-uri "http://order-service:8083/{proxy}" `
    --integration-method ANY `
    --payload-format-version 1.0 `
    --query 'IntegrationId' --output text

Write-Host "  [OK] order-service integration: $ORDER_INT" -ForegroundColor Green

# Create Routes (path-based routing)
Write-Host ""
Write-Host "Creating routes..." -ForegroundColor Cyan

$null = awslocal apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "ANY /users/{proxy+}" `
    --target "integrations/$USER_INT"

Write-Host "  [OK] /users/* -> user-service:8081" -ForegroundColor Green

$null = awslocal apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "ANY /shipments/{proxy+}" `
    --target "integrations/$SHIPMENT_INT"

Write-Host "  [OK] /shipments/* -> shipment-service:8082" -ForegroundColor Green

$null = awslocal apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "ANY /orders/{proxy+}" `
    --target "integrations/$ORDER_INT"

Write-Host "  [OK] /orders/* -> order-service:8083" -ForegroundColor Green

# Create Stage
Write-Host ""
Write-Host "Creating production stage..." -ForegroundColor Cyan

$null = awslocal apigatewayv2 create-stage `
    --api-id $API_ID `
    --stage-name prod `
    --auto-deploy

Write-Host "  [OK] Stage 'prod' created" -ForegroundColor Green
Write-Host "  [INFO] Rate limiting configuration requires additional setup in LocalStack Pro" -ForegroundColor Yellow

# Get the Gateway URL
$GATEWAY_URL = "$LOCALSTACK_ENDPOINT/restapis/$API_ID/prod/_user_request_"

Write-Host ""
Write-Host "API Gateway setup complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Gateway URL: $GATEWAY_URL" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test the gateway:"
Write-Host "  curl $GATEWAY_URL/users"
Write-Host "  curl $GATEWAY_URL/shipments"
Write-Host "  curl $GATEWAY_URL/orders"
Write-Host ""
Write-Host "Save API ID for later: " -NoNewline
Write-Host "`$env:DORNACH_API_ID=`"$API_ID`"" -ForegroundColor Yellow
