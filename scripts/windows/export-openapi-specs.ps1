# Export OpenAPI specifications from running services
# This script fetches the OpenAPI specs and saves them as YAML files

$ErrorActionPreference = "Stop"

$OPENAPI_DIR = Join-Path $PSScriptRoot "..\..\openapi"
New-Item -ItemType Directory -Force -Path $OPENAPI_DIR | Out-Null

Write-Host "Exporting OpenAPI specifications..." -ForegroundColor Cyan

# Check if services are running
function Check-Service {
    param (
        [string]$service,
        [int]$port
    )

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:${port}/actuator/health" -UseBasicParsing -ErrorAction SilentlyContinue
        return $true
    } catch {
        Write-Host "Error: ${service} is not running on port ${port}" -ForegroundColor Red
        Write-Host "Please start the service with: mvn spring-boot:run -pl ${service}"
        return $false
    }
}

# Export user-service spec
if (Check-Service -service "user-service" -port 8081) {
    Write-Host "Exporting user-service specification..." -ForegroundColor Yellow
    $spec = Invoke-WebRequest -Uri "http://localhost:8081/v3/api-docs.yaml" -UseBasicParsing
    $spec.Content | Out-File -FilePath (Join-Path $OPENAPI_DIR "user-service.yaml") -Encoding UTF8
    Write-Host "user-service.yaml exported" -ForegroundColor Green
}

# Export shipment-service spec
if (Check-Service -service "shipment-service" -port 8082) {
    Write-Host "Exporting shipment-service specification..." -ForegroundColor Yellow
    $spec = Invoke-WebRequest -Uri "http://localhost:8082/v3/api-docs.yaml" -UseBasicParsing
    $spec.Content | Out-File -FilePath (Join-Path $OPENAPI_DIR "shipment-service.yaml") -Encoding UTF8
    Write-Host "shipment-service.yaml exported" -ForegroundColor Green
}

# Export order-service spec
if (Check-Service -service "order-service" -port 8083) {
    Write-Host "Exporting order-service specification..." -ForegroundColor Yellow
    $spec = Invoke-WebRequest -Uri "http://localhost:8083/v3/api-docs.yaml" -UseBasicParsing
    $spec.Content | Out-File -FilePath (Join-Path $OPENAPI_DIR "order-service.yaml") -Encoding UTF8
    Write-Host "order-service.yaml exported" -ForegroundColor Green
}

Write-Host ""
Write-Host "OpenAPI specifications exported to $OPENAPI_DIR/" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Review the specifications in $OPENAPI_DIR/"
Write-Host "  2. Generate client SDKs with: .\generate-clients.ps1"
Write-Host "  3. View Swagger UI at:"
Write-Host "     - http://localhost:8081/swagger-ui.html (user-service)"
Write-Host "     - http://localhost:8082/swagger-ui.html (shipment-service)"
Write-Host "     - http://localhost:8083/swagger-ui.html (order-service)"
