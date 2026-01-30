# Test script for API Gateway routing

$ErrorActionPreference = "Stop"

if (-not $env:DORNACH_API_ID) {
    Write-Host "DORNACH_API_ID not set" -ForegroundColor Red
    Write-Host "   Run setup-gateway.ps1 first"
    exit 1
}

$LOCALSTACK_ENDPOINT = if ($env:LOCALSTACK_ENDPOINT) { $env:LOCALSTACK_ENDPOINT } else { "http://localhost:4566" }
$GATEWAY_URL = "$LOCALSTACK_ENDPOINT/restapis/$env:DORNACH_API_ID/prod/_user_request_"

Write-Host "Testing API Gateway routing..." -ForegroundColor Cyan
Write-Host ""

# Test user-service route (via health endpoint)
Write-Host "1. Testing /users route..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$GATEWAY_URL/users/actuator/health" -UseBasicParsing -ErrorAction SilentlyContinue
    $HTTP_CODE = $response.StatusCode
} catch {
    $HTTP_CODE = $_.Exception.Response.StatusCode.value__
    if (-not $HTTP_CODE) { $HTTP_CODE = "Error" }
}

if ($HTTP_CODE -eq 200) {
    Write-Host "   /users -> user-service (HTTP $HTTP_CODE)" -ForegroundColor Green
} else {
    Write-Host "   /users -> HTTP $HTTP_CODE" -ForegroundColor Red
}

# Test shipment-service route (via health endpoint)
Write-Host ""
Write-Host "2. Testing /shipments route..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$GATEWAY_URL/shipments/actuator/health" -UseBasicParsing -ErrorAction SilentlyContinue
    $HTTP_CODE = $response.StatusCode
} catch {
    $HTTP_CODE = $_.Exception.Response.StatusCode.value__
    if (-not $HTTP_CODE) { $HTTP_CODE = "Error" }
}

if ($HTTP_CODE -eq 200) {
    Write-Host "   /shipments -> shipment-service (HTTP $HTTP_CODE)" -ForegroundColor Green
} else {
    Write-Host "   /shipments -> HTTP $HTTP_CODE" -ForegroundColor Red
}

# Test order-service route (via health endpoint)
Write-Host ""
Write-Host "3. Testing /orders route..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$GATEWAY_URL/orders/actuator/health" -UseBasicParsing -ErrorAction SilentlyContinue
    $HTTP_CODE = $response.StatusCode
} catch {
    $HTTP_CODE = $_.Exception.Response.StatusCode.value__
    if (-not $HTTP_CODE) { $HTTP_CODE = "Error" }
}

if ($HTTP_CODE -eq 200) {
    Write-Host "   /orders -> order-service (HTTP $HTTP_CODE)" -ForegroundColor Green
} else {
    Write-Host "   /orders -> HTTP $HTTP_CODE" -ForegroundColor Red
}

# Test rate limiting (send 105 requests quickly)
Write-Host ""
Write-Host "4. Testing rate limiting..." -ForegroundColor Yellow
Write-Host "   Note: Rate limiting requires additional configuration in LocalStack Pro" -ForegroundColor Gray
Write-Host "   Skipping rate limit test for now"

Write-Host ""
Write-Host "Gateway tests complete" -ForegroundColor Green
