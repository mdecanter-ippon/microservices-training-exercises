# Keycloak Setup Script for Dornach Microservices Training
# This script configures Keycloak with realm, clients, roles, and users

$ErrorActionPreference = "Stop"

$KEYCLOAK_URL = if ($env:KEYCLOAK_URL) { $env:KEYCLOAK_URL } else { "http://localhost:8080" }
$ADMIN_USER = if ($env:KEYCLOAK_ADMIN) { $env:KEYCLOAK_ADMIN } else { "admin" }
$ADMIN_PASSWORD = if ($env:KEYCLOAK_ADMIN_PASSWORD) { $env:KEYCLOAK_ADMIN_PASSWORD } else { "admin" }
$REALM_FILE = Join-Path $PSScriptRoot "..\keycloak\dornach-realm.json"

Write-Host "Setting up Keycloak for Dornach Training..." -ForegroundColor Cyan
Write-Host ""

# Wait for Keycloak to be ready
Write-Host "Waiting for Keycloak to be ready..." -ForegroundColor Yellow
$MAX_RETRIES = 30
$RETRY_COUNT = 0

while ($true) {
    try {
        $response = Invoke-WebRequest -Uri "$KEYCLOAK_URL/realms/master" -UseBasicParsing -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            break
        }
    } catch {
        # Continue waiting
    }

    $RETRY_COUNT++
    if ($RETRY_COUNT -ge $MAX_RETRIES) {
        Write-Host "Error: Keycloak did not become ready in time" -ForegroundColor Red
        Write-Host "   Please check: docker-compose logs keycloak"
        exit 1
    }
    Write-Host "   Attempt $RETRY_COUNT/$MAX_RETRIES..."
    Start-Sleep -Seconds 2
}

Write-Host "Keycloak is ready!" -ForegroundColor Green
Write-Host ""

# Get admin access token
Write-Host "Obtaining admin token..." -ForegroundColor Yellow
$tokenBody = @{
    username = $ADMIN_USER
    password = $ADMIN_PASSWORD
    grant_type = "password"
    client_id = "admin-cli"
}

try {
    $tokenResponse = Invoke-RestMethod -Uri "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" `
        -Method Post -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
    $TOKEN = $tokenResponse.access_token
} catch {
    Write-Host "Error: Failed to obtain admin token" -ForegroundColor Red
    Write-Host "   $_"
    exit 1
}

if (-not $TOKEN) {
    Write-Host "Error: Failed to obtain admin token" -ForegroundColor Red
    exit 1
}

Write-Host "Admin token obtained" -ForegroundColor Green
Write-Host ""

$headers = @{
    Authorization = "Bearer $TOKEN"
}

# Check if realm already exists
Write-Host "Checking if 'dornach' realm exists..." -ForegroundColor Yellow
try {
    $realmCheck = Invoke-WebRequest -Uri "$KEYCLOAK_URL/admin/realms/dornach" `
        -Headers $headers -UseBasicParsing -ErrorAction SilentlyContinue
    $REALM_EXISTS = $true
} catch {
    $REALM_EXISTS = $false
}

if ($REALM_EXISTS) {
    Write-Host "Realm 'dornach' already exists" -ForegroundColor Yellow
    $reply = Read-Host "   Do you want to delete and recreate it? (y/N)"
    if ($reply -match "^[Yy]$") {
        Write-Host "Deleting existing realm..." -ForegroundColor Yellow
        Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/dornach" `
            -Method Delete -Headers $headers
        Write-Host "Realm deleted" -ForegroundColor Green
    } else {
        Write-Host "Skipping realm creation" -ForegroundColor Yellow
        exit 0
    }
}

# Import realm from JSON file
Write-Host "Importing realm configuration from JSON..." -ForegroundColor Yellow

if (-not (Test-Path $REALM_FILE)) {
    Write-Host "Error: Realm configuration file not found: $REALM_FILE" -ForegroundColor Red
    exit 1
}

$realmJson = Get-Content $REALM_FILE -Raw
$importHeaders = @{
    Authorization = "Bearer $TOKEN"
    "Content-Type" = "application/json"
}

try {
    Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms" `
        -Method Post -Headers $importHeaders -Body $realmJson
} catch {
    Write-Host "Import warning: $_" -ForegroundColor Yellow
}

Write-Host "Realm 'dornach' created successfully" -ForegroundColor Green
Write-Host ""

# Verify realm configuration
Write-Host "Verifying configuration..." -ForegroundColor Green

# Check realm
$realmInfo = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/dornach" -Headers $headers
Write-Host "   Realm: $($realmInfo.realm)" -ForegroundColor White

# Check client
$clients = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/dornach/clients" -Headers $headers
$webClient = $clients | Where-Object { $_.clientId -eq "dornach-web" }
Write-Host "   Client: $($webClient.clientId)" -ForegroundColor White

# Check roles
$roles = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/dornach/roles" -Headers $headers
$roleNames = ($roles | Where-Object { $_.name -match "user|admin" } | ForEach-Object { $_.name }) -join ", "
Write-Host "   Roles: $roleNames" -ForegroundColor White

# Check users
$users = Invoke-RestMethod -Uri "$KEYCLOAK_URL/admin/realms/dornach/users" -Headers $headers
$userNames = ($users | ForEach-Object { $_.username }) -join ", "
Write-Host "   Users: $userNames" -ForegroundColor White

Write-Host ""

# Configure M2M (Machine-to-Machine) service account
Write-Host "Configuring M2M service account..." -ForegroundColor Yellow

# Get order-service-client ID
$orderClient = $clients | Where-Object { $_.clientId -eq "order-service-client" }

if (-not $orderClient) {
    Write-Host "Warning: order-service-client not found, skipping M2M configuration" -ForegroundColor Yellow
} else {
    $ORDER_CLIENT_ID = $orderClient.id

    # Get service account user ID
    $serviceAccountUser = Invoke-RestMethod `
        -Uri "$KEYCLOAK_URL/admin/realms/dornach/clients/$ORDER_CLIENT_ID/service-account-user" `
        -Headers $headers
    $SERVICE_ACCOUNT_USER_ID = $serviceAccountUser.id

    # Get service-caller role
    $serviceCallerRole = Invoke-RestMethod `
        -Uri "$KEYCLOAK_URL/admin/realms/dornach/roles/service-caller" `
        -Headers $headers

    $roleMapping = @(
        @{
            id = $serviceCallerRole.id
            name = $serviceCallerRole.name
        }
    )

    # Assign service-caller role to service account
    Invoke-RestMethod `
        -Uri "$KEYCLOAK_URL/admin/realms/dornach/users/$SERVICE_ACCOUNT_USER_ID/role-mappings/realm" `
        -Method Post -Headers $importHeaders -Body ($roleMapping | ConvertTo-Json)

    Write-Host "   M2M Client: order-service-client" -ForegroundColor White
    Write-Host "   Service Account Role: service-caller" -ForegroundColor White
}

Write-Host ""
Write-Host "Keycloak setup completed successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Configuration Summary:" -ForegroundColor Cyan
Write-Host "   - Realm: dornach"
Write-Host "   - Clients:"
Write-Host "     - dornach-web (public) - for H2M authentication"
Write-Host "     - order-service-client (confidential) - for M2M authentication"
Write-Host "   - Roles: user, admin, service-caller"
Write-Host "   - Users:"
Write-Host "     - alice (password: alice123) - role: user"
Write-Host "     - bob (password: bob123) - roles: user, admin"
Write-Host "   - Service Accounts:"
Write-Host "     - service-account-order-service-client - role: service-caller"
Write-Host ""
Write-Host "Access Keycloak:" -ForegroundColor Cyan
Write-Host "   - Admin Console: $KEYCLOAK_URL/admin"
Write-Host "   - Realm: $KEYCLOAK_URL/realms/dornach"
Write-Host ""
Write-Host "Test authentication:" -ForegroundColor Cyan
Write-Host "   Invoke-RestMethod -Uri '$KEYCLOAK_URL/realms/dornach/protocol/openid-connect/token' ``"
Write-Host "     -Method Post -Body @{client_id='dornach-web'; username='alice'; password='alice123'; grant_type='password'}"
Write-Host ""
