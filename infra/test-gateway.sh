#!/bin/bash

# Test script for API Gateway routing

set -e

if [ -z "$DORNACH_API_ID" ]; then
    echo "❌ DORNACH_API_ID not set"
    echo "   Run setup-gateway.sh first"
    exit 1
fi

LOCALSTACK_ENDPOINT=${LOCALSTACK_ENDPOINT:-http://localhost:4566}
GATEWAY_URL="$LOCALSTACK_ENDPOINT/restapis/$DORNACH_API_ID/prod/_user_request_"

echo "🧪 Testing API Gateway routing..."
echo ""

# Test user-service route (via health endpoint)
echo "1️⃣  Testing /users route..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/users/actuator/health")

if [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ /users → user-service (HTTP $HTTP_CODE)"
else
    echo "   ❌ /users → HTTP $HTTP_CODE"
fi

# Test shipment-service route (via health endpoint)
echo ""
echo "2️⃣  Testing /shipments route..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/shipments/actuator/health")

if [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ /shipments → shipment-service (HTTP $HTTP_CODE)"
else
    echo "   ❌ /shipments → HTTP $HTTP_CODE"
fi

# Test order-service route (via health endpoint)
echo ""
echo "3️⃣  Testing /orders route..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/orders/actuator/health")

if [ "$HTTP_CODE" = "200" ]; then
    echo "   ✅ /orders → order-service (HTTP $HTTP_CODE)"
else
    echo "   ❌ /orders → HTTP $HTTP_CODE"
fi

# Test rate limiting (send 105 requests quickly)
echo ""
echo "4️⃣  Testing rate limiting..."
echo "   ℹ️  Note: Rate limiting requires additional configuration in LocalStack Pro"
echo "   Skipping rate limit test for now"

echo ""
echo "✅ Gateway tests complete"
