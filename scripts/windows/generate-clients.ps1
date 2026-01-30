# Generate client SDKs from OpenAPI specifications
# Supports Java, TypeScript, and other languages

$ErrorActionPreference = "Stop"

$OPENAPI_DIR = Join-Path $PSScriptRoot "..\..\openapi"
$GENERATED_DIR = Join-Path $PSScriptRoot "..\..\generated-clients"

# Check if OpenAPI Generator CLI is installed
$openApiGenCmd = Get-Command openapi-generator-cli -ErrorAction SilentlyContinue

if (-not $openApiGenCmd) {
    Write-Host "OpenAPI Generator CLI not found." -ForegroundColor Yellow
    Write-Host "Installing via npm..."
    npm install -g @openapitools/openapi-generator-cli
}

New-Item -ItemType Directory -Force -Path $GENERATED_DIR | Out-Null

Write-Host "Generating client SDKs from OpenAPI specifications..." -ForegroundColor Cyan

# Function to generate a client
function Generate-Client {
    param (
        [string]$service,
        [string]$language
    )

    $specFile = Join-Path $OPENAPI_DIR "${service}.yaml"
    $outputDir = Join-Path $GENERATED_DIR "$language\${service}-client"

    if (-not (Test-Path $specFile)) {
        Write-Host "Warning: ${specFile} not found. Run export-openapi-specs.ps1 first." -ForegroundColor Yellow
        return $false
    }

    Write-Host "Generating ${language} client for ${service}..." -ForegroundColor Yellow

    openapi-generator-cli generate `
        -i $specFile `
        -g $language `
        -o $outputDir `
        --additional-properties=apiPackage=com.dornach.${service}.client.api,modelPackage=com.dornach.${service}.client.model

    Write-Host "${language} client generated at ${outputDir}" -ForegroundColor Green
    return $true
}

# Generate Java clients
Write-Host ""
Write-Host "=== Generating Java Clients ===" -ForegroundColor Cyan
Generate-Client -service "user-service" -language "java"
Generate-Client -service "shipment-service" -language "java"
Generate-Client -service "order-service" -language "java"

# Generate TypeScript clients (for frontend)
Write-Host ""
Write-Host "=== Generating TypeScript Clients ===" -ForegroundColor Cyan
Generate-Client -service "user-service" -language "typescript-axios"
Generate-Client -service "shipment-service" -language "typescript-axios"
Generate-Client -service "order-service" -language "typescript-axios"

Write-Host ""
Write-Host "Client SDKs generated successfully in $GENERATED_DIR/" -ForegroundColor Green
Write-Host ""
Write-Host "Example usage (Java):" -ForegroundColor Cyan
Write-Host "  ApiClient client = new ApiClient();"
Write-Host "  client.setBasePath(`"http://localhost:8081`");"
Write-Host "  UsersApi api = new UsersApi(client);"
Write-Host "  List<UserResponse> users = api.getAllUsers();"
