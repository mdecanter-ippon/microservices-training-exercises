#!/bin/bash

# LocalStack API Gateway v2 Setup Script for Dornach Microservices
# This script creates an HTTP API with path-based routing and rate limiting

set -e

LOCALSTACK_ENDPOINT=${LOCALSTACK_ENDPOINT:-http://localhost:4566}

echo "🚀 Setting up Dornach API Gateway with LocalStack..."
echo ""

# Check if LocalStack is running
if ! curl -s "$LOCALSTACK_ENDPOINT/_localstack/health" > /dev/null 2>&1; then
    echo "❌ LocalStack is not running at $LOCALSTACK_ENDPOINT"
    echo "   Start it with: docker-compose up -d localstack"
    exit 1
fi

echo "✓ LocalStack is running"

# Create HTTP API (v2 - faster and cheaper than REST API v1)
echo ""
echo "📡 Creating HTTP API Gateway..."
API_ID=$(awslocal apigatewayv2 create-api \
    --name dornach-gateway \
    --protocol-type HTTP \
    --description "Dornach microservices API Gateway" \
    --query 'ApiId' --output text)

echo "✓ API created with ID: $API_ID"

# Create VPC Link (for Docker network communication)
echo ""
echo "🔗 Creating integrations for each service..."

# For LocalStack, we use HTTP_PROXY integrations pointing to service URLs
# In Docker network, services are accessible by their container names

# User Service Integration
USER_INT=$(awslocal apigatewayv2 create-integration \
    --api-id $API_ID \
    --integration-type HTTP_PROXY \
    --integration-uri http://host.docker.internal:8081/users \
    --integration-method ANY \
    --payload-format-version 1.0 \
    --request-parameters 'overwrite:path=$request.path' \
    --query 'IntegrationId' --output text)

echo "  ✓ user-service integration: $USER_INT"

# Shipment Service Integration
SHIPMENT_INT=$(awslocal apigatewayv2 create-integration \
    --api-id $API_ID \
    --integration-type HTTP_PROXY \
    --integration-uri http://host.docker.internal:8082/shipments \
    --integration-method ANY \
    --payload-format-version 1.0 \
    --request-parameters 'overwrite:path=$request.path' \
    --query 'IntegrationId' --output text)

echo "  ✓ shipment-service integration: $SHIPMENT_INT"

# Order Service Integration
ORDER_INT=$(awslocal apigatewayv2 create-integration \
    --api-id $API_ID \
    --integration-type HTTP_PROXY \
    --integration-uri http://host.docker.internal:8083/orders \
    --integration-method ANY \
    --payload-format-version 1.0 \
    --request-parameters 'overwrite:path=$request.path' \
    --query 'IntegrationId' --output text)

echo "  ✓ order-service integration: $ORDER_INT"

# Create Routes (path-based routing)
# Note: We need both base routes (/users) and proxy routes (/users/{proxy+})
echo ""
echo "🛣️  Creating routes..."

# User routes
awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "ANY /users" \
    --target "integrations/$USER_INT" \
    > /dev/null

awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "ANY /users/{proxy+}" \
    --target "integrations/$USER_INT" \
    > /dev/null

echo "  ✓ /users → user-service:8081"

# Shipment routes
awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "ANY /shipments" \
    --target "integrations/$SHIPMENT_INT" \
    > /dev/null

awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "ANY /shipments/{proxy+}" \
    --target "integrations/$SHIPMENT_INT" \
    > /dev/null

echo "  ✓ /shipments → shipment-service:8082"

# Order routes
awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "ANY /orders" \
    --target "integrations/$ORDER_INT" \
    > /dev/null

awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "ANY /orders/{proxy+}" \
    --target "integrations/$ORDER_INT" \
    > /dev/null

echo "  ✓ /orders → order-service:8083"

# Create Stage with throttling
echo ""
echo "⚙️  Creating production stage..."

awslocal apigatewayv2 create-stage \
    --api-id $API_ID \
    --stage-name prod \
    --auto-deploy \
    > /dev/null

echo "  ✓ Stage 'prod' created"
echo "  ℹ️  Note: Rate limiting configuration requires additional setup in LocalStack Pro"

# Get the Gateway URL
GATEWAY_URL="$LOCALSTACK_ENDPOINT/restapis/$API_ID/prod/_user_request_"

# Update Bruno environment with new API ID
BRUNO_ENV="../bruno/environments/Direct.bru"
if [ -f "$BRUNO_ENV" ]; then
    sed -i.bak "s/api_id: .*/api_id: $API_ID/" "$BRUNO_ENV" && rm -f "$BRUNO_ENV.bak"
    echo ""
    echo "🔧 Bruno environment updated with API ID: $API_ID"
fi

echo ""
echo "✅ API Gateway setup complete!"
echo ""
echo "Gateway URL: $GATEWAY_URL"
echo ""
echo "Test the gateway:"
echo "  curl $GATEWAY_URL/users"
echo "  curl $GATEWAY_URL/shipments"
echo "  curl $GATEWAY_URL/orders"
echo ""
echo "Save API ID for cleanup: export DORNACH_API_ID=$API_ID"
