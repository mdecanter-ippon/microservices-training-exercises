#!/bin/bash

# Keycloak Setup Script for Dornach Microservices Training
# This script configures Keycloak with realm, clients, roles, and users

set -e

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
ADMIN_USER=${KEYCLOAK_ADMIN:-admin}
ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}
REALM_FILE="$(dirname "$0")/keycloak/dornach-realm.json"

echo "🔐 Setting up Keycloak for Dornach Training..."
echo ""

# Wait for Keycloak to be ready
echo "⏳ Waiting for Keycloak to be ready..."
MAX_RETRIES=30
RETRY_COUNT=0

until curl -sf "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo "❌ Error: Keycloak did not become ready in time"
        echo "   Please check: docker-compose logs keycloak"
        exit 1
    fi
    echo "   Attempt $RETRY_COUNT/$MAX_RETRIES..."
    sleep 2
done

echo "✓ Keycloak is ready!"
echo ""

# Get admin access token
echo "🔑 Obtaining admin token..."
TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=${ADMIN_USER}" \
    -d "password=${ADMIN_PASSWORD}" \
    -d "grant_type=password" \
    -d "client_id=admin-cli")

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ Error: Failed to obtain admin token"
    echo "   Response: $TOKEN_RESPONSE"
    exit 1
fi

echo "✓ Admin token obtained"
echo ""

# Check if realm already exists
echo "🔍 Checking if 'dornach' realm exists..."
REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach")

if [ "$REALM_EXISTS" = "200" ]; then
    echo "⚠️  Realm 'dornach' already exists"
    read -p "   Do you want to delete and recreate it? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🗑️  Deleting existing realm..."
        curl -s -X DELETE \
            -H "Authorization: Bearer ${TOKEN}" \
            "${KEYCLOAK_URL}/admin/realms/dornach"
        echo "✓ Realm deleted"
    else
        echo "⏭️  Skipping realm creation"
        exit 0
    fi
fi

# Import realm from JSON file
echo "📦 Importing realm configuration from JSON..."

if [ ! -f "$REALM_FILE" ]; then
    echo "❌ Error: Realm configuration file not found: $REALM_FILE"
    exit 1
fi

IMPORT_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"$REALM_FILE")

if [ -n "$IMPORT_RESPONSE" ]; then
    echo "⚠️  Import response: $IMPORT_RESPONSE"
fi

echo "✓ Realm 'dornach' created successfully"
echo ""

# Verify realm configuration
echo "✅ Verifying configuration..."

# Check realm
REALM_CHECK=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach" | jq -r '.realm')
echo "   ✓ Realm: $REALM_CHECK"

# Check client
CLIENT_CHECK=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach/clients" | jq -r '.[].clientId' | grep dornach-web)
echo "   ✓ Client: $CLIENT_CHECK"

# Check roles
ROLES=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach/roles" | jq -r '.[].name' | grep -E "user|admin" | tr '\n' ', ' | sed 's/,$//')
echo "   ✓ Roles: $ROLES"

# Check users
USERS=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach/users" | jq -r '.[].username' | tr '\n' ', ' | sed 's/,$//')
echo "   ✓ Users: $USERS"

echo ""

# Configure M2M (Machine-to-Machine) service account
echo "🤖 Configuring M2M service account..."

# Get order-service-client ID
ORDER_CLIENT_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
    "${KEYCLOAK_URL}/admin/realms/dornach/clients" | jq -r '.[] | select(.clientId=="order-service-client") | .id')

if [ -z "$ORDER_CLIENT_ID" ] || [ "$ORDER_CLIENT_ID" = "null" ]; then
    echo "⚠️  Warning: order-service-client not found, skipping M2M configuration"
else
    # Get service account user ID
    SERVICE_ACCOUNT_USER_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
        "${KEYCLOAK_URL}/admin/realms/dornach/clients/${ORDER_CLIENT_ID}/service-account-user" | jq -r '.id')

    # Get service-caller role ID
    SERVICE_CALLER_ROLE=$(curl -s -H "Authorization: Bearer ${TOKEN}" \
        "${KEYCLOAK_URL}/admin/realms/dornach/roles/service-caller" | jq -c '{id: .id, name: .name}')

    # Assign service-caller role to service account
    curl -s -X POST -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "[${SERVICE_CALLER_ROLE}]" \
        "${KEYCLOAK_URL}/admin/realms/dornach/users/${SERVICE_ACCOUNT_USER_ID}/role-mappings/realm" > /dev/null

    echo "   ✓ M2M Client: order-service-client"
    echo "   ✓ Service Account Role: service-caller"
fi

echo ""
echo "🎉 Keycloak setup completed successfully!"
echo ""
echo "📋 Configuration Summary:"
echo "   - Realm: dornach"
echo "   - Clients:"
echo "     • dornach-web (public) - for H2M authentication"
echo "     • order-service-client (confidential) - for M2M authentication"
echo "   - Roles: user, admin, service-caller"
echo "   - Users:"
echo "     • alice (password: alice123) - role: user"
echo "     • bob (password: bob123) - roles: user, admin"
echo "   - Service Accounts:"
echo "     • service-account-order-service-client - role: service-caller"
echo ""
echo "🔗 Access Keycloak:"
echo "   - Admin Console: ${KEYCLOAK_URL}/admin"
echo "   - Realm: ${KEYCLOAK_URL}/realms/dornach"
echo ""
echo "🧪 Test authentication:"
echo "   curl -X POST ${KEYCLOAK_URL}/realms/dornach/protocol/openid-connect/token \\"
echo "     -d 'client_id=dornach-web' \\"
echo "     -d 'username=alice' \\"
echo "     -d 'password=alice123' \\"
echo "     -d 'grant_type=password'"
echo ""

