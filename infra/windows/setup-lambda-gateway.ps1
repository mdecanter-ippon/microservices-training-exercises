# LocalStack API Gateway v2 Setup Script for Order Validation Lambda
# This script creates an HTTP API that routes to the Order Validation Lambda

$ErrorActionPreference = "Stop"

$LOCALSTACK_ENDPOINT = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { "http://localhost:4566" }
$API_NAME = "dornach-lambda-api"
$FUNCTION_NAME = "order-validation"

Write-Host "=== Setting up API Gateway for Order Validation Lambda ===" -ForegroundColor Cyan
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

# Delete existing API (if exists)
Write-Host ""
Write-Host "Checking for existing API..." -ForegroundColor Yellow

$existingApis = aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 get-apis --query "Items[?Name=='$API_NAME'].ApiId" --output text 2>$null
if ($existingApis -and $existingApis.Trim() -ne "" -and $existingApis.Trim() -ne "None") {
    Write-Host "  Deleting existing API: $existingApis" -ForegroundColor White
    aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 delete-api --api-id $existingApis.Trim() 2>$null
}

# Create HTTP API
Write-Host ""
Write-Host "Creating HTTP API..." -ForegroundColor Yellow

$API_ID = (aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-api `
    --name $API_NAME `
    --protocol-type HTTP `
    --query 'ApiId' --output text).Trim()

Write-Host "  API created with ID: $API_ID" -ForegroundColor Green

# Get Lambda ARN
Write-Host ""
Write-Host "Getting Lambda function ARN..." -ForegroundColor Yellow

$LAMBDA_ARN = (aws --endpoint-url $LOCALSTACK_ENDPOINT lambda get-function `
    --function-name $FUNCTION_NAME `
    --query 'Configuration.FunctionArn' --output text).Trim()

Write-Host "  Lambda ARN: $LAMBDA_ARN" -ForegroundColor White

# Create Lambda integration
Write-Host ""
Write-Host "Creating Lambda integration..." -ForegroundColor Yellow

$INTEGRATION_ID = (aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-integration `
    --api-id $API_ID `
    --integration-type AWS_PROXY `
    --integration-uri $LAMBDA_ARN `
    --payload-format-version "2.0" `
    --query 'IntegrationId' --output text).Trim()

Write-Host "  Integration created: $INTEGRATION_ID" -ForegroundColor Green

# Create route for POST /validate
Write-Host ""
Write-Host "Creating route..." -ForegroundColor Yellow

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-route `
    --api-id $API_ID `
    --route-key "POST /validate" `
    --target "integrations/$INTEGRATION_ID" | Out-Null

Write-Host "  POST /validate -> Lambda" -ForegroundColor White

# Create default stage with auto-deploy
Write-Host ""
Write-Host "Creating stage..." -ForegroundColor Yellow

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 create-stage `
    --api-id $API_ID `
    --stage-name '$default' `
    --auto-deploy | Out-Null

Write-Host "  Default stage created with auto-deploy" -ForegroundColor Green

# Build the API endpoint
$API_ENDPOINT = "$LOCALSTACK_ENDPOINT/restapis/$API_ID/`$default/_user_request_"

Write-Host ""
Write-Host "========================================"  -ForegroundColor Green
Write-Host "API Gateway configured for Order Validation!" -ForegroundColor Green
Write-Host "========================================"  -ForegroundColor Green
Write-Host ""
Write-Host "API ID: $API_ID" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test endpoint:" -ForegroundColor Cyan
Write-Host ""
Write-Host "POST /validate:" -ForegroundColor Yellow
Write-Host "  Invoke-RestMethod -Method POST -Uri '$API_ENDPOINT/validate' ``" -ForegroundColor White
Write-Host "    -ContentType 'application/json' ``" -ForegroundColor White
Write-Host "    -Body '{`"userId`":`"11111111-1111-1111-1111-111111111111`",`"quantity`":2,`"totalPrice`":100}'" -ForegroundColor White
Write-Host ""
Write-Host "Save API ID: `$env:LAMBDA_API_ID = '$API_ID'" -ForegroundColor Gray
Write-Host ""
