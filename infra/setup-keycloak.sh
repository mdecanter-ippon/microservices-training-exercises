#!/bin/bash

# Keycloak Setup Script for Dornach Training
# Creates realm, client, roles, and users for H2M authentication exercise

set -e

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
ADMIN_USER=${KEYCLOAK_ADMIN:-admin}
ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD:-admin}

echo "🔐 Setting up Keycloak for Dornach Training..."
echo ""

# Wait for Keycloak to be ready (using /realms/master endpoint)
echo "Waiting for Keycloak to be ready..."
until curl -sf "${KEYCLOAK_URL}/realms/master" > /dev/null 2>&1; do
    sleep 2
done
echo "✓ Keycloak is ready!"

# Get admin token
echo ""
echo "🔑 Getting admin token..."
TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=${ADMIN_USER}" \
    -d "password=${ADMIN_PASSWORD}" \
    -d "grant_type=password" \
    -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ Failed to get admin token. Check admin credentials."
    exit 1
fi
echo "✓ Admin token obtained"

# Create realm
echo ""
echo "🏰 Creating 'dornach' realm..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "realm": "dornach",
        "enabled": true,
        "registrationAllowed": false
    }' 2>/dev/null || true
echo "✓ Realm 'dornach' created (or already exists)"

# Create client
echo ""
echo "📱 Creating 'dornach-web' client..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "clientId": "dornach-web",
        "name": "Dornach Web Application",
        "enabled": true,
        "publicClient": true,
        "directAccessGrantsEnabled": true,
        "standardFlowEnabled": true,
        "redirectUris": ["*"],
        "webOrigins": ["*"]
    }' 2>/dev/null || true
echo "✓ Client 'dornach-web' created (or already exists)"

# Create M2M client for service-to-service communication
echo ""
echo "🤖 Creating 'dornach-services' M2M client..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/clients" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "clientId": "dornach-services",
        "name": "Dornach Services (M2M)",
        "enabled": true,
        "publicClient": false,
        "serviceAccountsEnabled": true,
        "directAccessGrantsEnabled": false,
        "standardFlowEnabled": false,
        "secret": "dornach-services-secret"
    }' 2>/dev/null || true
echo "✓ Client 'dornach-services' created (or already exists)"

# Create realm roles
echo ""
echo "👔 Creating realm roles..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/roles" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"name": "user", "description": "Regular user role"}' 2>/dev/null || true

curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/roles" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"name": "admin", "description": "Administrator role"}' 2>/dev/null || true
echo "✓ Roles 'user' and 'admin' created (or already exist)"

# Create user Alice (user role)
echo ""
echo "👤 Creating user 'alice'..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/users" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "alice",
        "email": "alice@dornach.com",
        "firstName": "Alice",
        "lastName": "User",
        "enabled": true,
        "emailVerified": true,
        "credentials": [{"type": "password", "value": "alice123", "temporary": false}]
    }' 2>/dev/null || true

# Get Alice's ID and assign role
ALICE_ID=$(curl -s "${KEYCLOAK_URL}/admin/realms/dornach/users?username=alice" \
    -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id')

if [ "$ALICE_ID" != "null" ] && [ -n "$ALICE_ID" ]; then
    USER_ROLE=$(curl -s "${KEYCLOAK_URL}/admin/realms/dornach/roles/user" \
        -H "Authorization: Bearer ${TOKEN}")
    curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/users/${ALICE_ID}/role-mappings/realm" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "[${USER_ROLE}]" 2>/dev/null || true
fi
echo "✓ User 'alice' created with 'user' role (password: alice123)"

# Create user Bob (admin role)
echo ""
echo "👤 Creating user 'bob'..."
curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/users" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "bob",
        "email": "bob@dornach.com",
        "firstName": "Bob",
        "lastName": "Admin",
        "enabled": true,
        "emailVerified": true,
        "credentials": [{"type": "password", "value": "bob123", "temporary": false}]
    }' 2>/dev/null || true

# Get Bob's ID and assign roles
BOB_ID=$(curl -s "${KEYCLOAK_URL}/admin/realms/dornach/users?username=bob" \
    -H "Authorization: Bearer ${TOKEN}" | jq -r '.[0].id')

if [ "$BOB_ID" != "null" ] && [ -n "$BOB_ID" ]; then
    ADMIN_ROLE=$(curl -s "${KEYCLOAK_URL}/admin/realms/dornach/roles/admin" \
        -H "Authorization: Bearer ${TOKEN}")
    USER_ROLE=$(curl -s "${KEYCLOAK_URL}/admin/realms/dornach/roles/user" \
        -H "Authorization: Bearer ${TOKEN}")
    curl -s -X POST "${KEYCLOAK_URL}/admin/realms/dornach/users/${BOB_ID}/role-mappings/realm" \
        -H "Authorization: Bearer ${TOKEN}" \
        -H "Content-Type: application/json" \
        -d "[${ADMIN_ROLE}, ${USER_ROLE}]" 2>/dev/null || true
fi
echo "✓ User 'bob' created with 'admin' role (password: bob123)"

echo ""
echo "✅ Keycloak setup complete!"
echo ""
echo "Summary:"
echo "  Realm:   dornach"
echo "  Client:  dornach-web (public, for testing)"
echo "  Client:  dornach-services (confidential, for M2M)"
echo "  Users:   alice (user role), bob (admin role)"
echo ""
echo "Test token generation:"
echo "  curl -X POST http://localhost:8080/realms/dornach/protocol/openid-connect/token \\"
echo "    -d 'client_id=dornach-web' \\"
echo "    -d 'username=alice' \\"
echo "    -d 'password=alice123' \\"
echo "    -d 'grant_type=password'"
echo ""
