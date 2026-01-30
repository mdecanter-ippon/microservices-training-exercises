# Cleanup LocalStack API Gateway

$ErrorActionPreference = "Stop"

$LOCALSTACK_ENDPOINT = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { "http://localhost:4566" }

if (-not $env:DORNACH_API_ID) {
    Write-Host "DORNACH_API_ID not set" -ForegroundColor Red
    Write-Host "   List APIs with: awslocal apigatewayv2 get-apis"
    Write-Host "   Then: `$env:DORNACH_API_ID = '<your-api-id>'"
    exit 1
}

Write-Host "Cleaning up API Gateway: $env:DORNACH_API_ID" -ForegroundColor Yellow

# Set dummy AWS credentials for LocalStack
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = "us-east-1"

aws --endpoint-url $LOCALSTACK_ENDPOINT apigatewayv2 delete-api --api-id $env:DORNACH_API_ID

Write-Host "API Gateway deleted" -ForegroundColor Green
