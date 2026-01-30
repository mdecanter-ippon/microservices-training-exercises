# LocalStack Lambda Deployment Script for Order Validation
# This script deploys the Order Validation Lambda to LocalStack

$ErrorActionPreference = "Stop"

$LOCALSTACK_ENDPOINT = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { "http://localhost:4566" }
$FUNCTION_NAME = "order-validation"
$JAR_PATH = "..\lambda-service\target\lambda-service-1.0.0-SNAPSHOT-aws.jar"
$HANDLER = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"

Write-Host "=== Deploying Order Validation Lambda ===" -ForegroundColor Cyan
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

# Check if JAR exists
if (-not (Test-Path $JAR_PATH)) {
    Write-Host "Error: JAR not found at $JAR_PATH" -ForegroundColor Red
    Write-Host "Run 'mvn clean package -DskipTests' in lambda-service first" -ForegroundColor Yellow
    exit 1
}

Write-Host "JAR found: $JAR_PATH" -ForegroundColor Green

# Set dummy AWS credentials for LocalStack
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = "us-east-1"

# Delete existing function (ignore error if doesn't exist)
Write-Host ""
Write-Host "Cleaning up existing function..." -ForegroundColor Yellow
try {
    aws --endpoint-url $LOCALSTACK_ENDPOINT lambda delete-function --function-name $FUNCTION_NAME 2>$null
    Write-Host "  Deleted existing function" -ForegroundColor White
} catch {
    Write-Host "  No existing function to delete" -ForegroundColor Gray
}

# Create IAM role for Lambda (if doesn't exist)
Write-Host ""
Write-Host "Creating IAM role..." -ForegroundColor Yellow
try {
    $trustPolicy = @{
        Version = "2012-10-17"
        Statement = @(
            @{
                Effect = "Allow"
                Principal = @{
                    Service = "lambda.amazonaws.com"
                }
                Action = "sts:AssumeRole"
            }
        )
    } | ConvertTo-Json -Depth 10 -Compress

    aws --endpoint-url $LOCALSTACK_ENDPOINT iam create-role `
        --role-name lambda-role `
        --assume-role-policy-document $trustPolicy 2>$null | Out-Null
    Write-Host "  IAM role created" -ForegroundColor White
} catch {
    Write-Host "  IAM role already exists" -ForegroundColor Gray
}

# Create the Lambda function
Write-Host ""
Write-Host "Creating Lambda function: $FUNCTION_NAME" -ForegroundColor Yellow

$envVars = @{
    Variables = @{
        SPRING_CLOUD_FUNCTION_DEFINITION = "orderValidationFunction"
    }
} | ConvertTo-Json -Compress

aws --endpoint-url $LOCALSTACK_ENDPOINT lambda create-function `
    --function-name $FUNCTION_NAME `
    --runtime java21 `
    --handler $HANDLER `
    --role "arn:aws:iam::000000000000:role/lambda-role" `
    --zip-file "fileb://$JAR_PATH" `
    --timeout 30 `
    --memory-size 512 `
    --environment $envVars | Out-Null

# Wait for function to be ready
Write-Host "Waiting for function to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Verify function is created
Write-Host ""
Write-Host "========================================"  -ForegroundColor Green
Write-Host "Order Validation Lambda deployed!" -ForegroundColor Green
Write-Host "========================================"  -ForegroundColor Green
Write-Host ""

# List functions
Write-Host "Deployed functions:" -ForegroundColor Cyan
aws --endpoint-url $LOCALSTACK_ENDPOINT lambda list-functions --query 'Functions[].{Name:FunctionName,Runtime:Runtime,Memory:MemorySize}' --output table

Write-Host ""
Write-Host "Test with:" -ForegroundColor Cyan
Write-Host "  aws --endpoint-url $LOCALSTACK_ENDPOINT lambda invoke --function-name $FUNCTION_NAME ``" -ForegroundColor White
Write-Host "    --payload '{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":2,\"totalPrice\":100}' ``" -ForegroundColor White
Write-Host "    --cli-binary-format raw-in-base64-out response.json; Get-Content response.json" -ForegroundColor White
Write-Host ""
