#!/bin/bash
set -e

API_NAME="dornach-lambda-api"
FUNCTION_NAME="order-validation"

echo "=== Setting up API Gateway for Order Validation Lambda ==="

# Delete existing API (if exists)
EXISTING_API=$(awslocal apigatewayv2 get-apis --query "Items[?Name=='$API_NAME'].ApiId" --output text 2>/dev/null || echo "")
if [ -n "$EXISTING_API" ] && [ "$EXISTING_API" != "None" ]; then
    echo "Deleting existing API: $EXISTING_API"
    awslocal apigatewayv2 delete-api --api-id $EXISTING_API
fi

# Create HTTP API
echo "Creating HTTP API..."
API_ID=$(awslocal apigatewayv2 create-api \
    --name $API_NAME \
    --protocol-type HTTP \
    --query 'ApiId' --output text)

echo "API created with ID: $API_ID"

# Get Lambda ARN
LAMBDA_ARN=$(awslocal lambda get-function \
    --function-name $FUNCTION_NAME \
    --query 'Configuration.FunctionArn' --output text)

echo "Lambda ARN: $LAMBDA_ARN"

# Create Lambda integration
echo "Creating Lambda integration..."
INTEGRATION_ID=$(awslocal apigatewayv2 create-integration \
    --api-id $API_ID \
    --integration-type AWS_PROXY \
    --integration-uri $LAMBDA_ARN \
    --payload-format-version "2.0" \
    --query 'IntegrationId' --output text)

echo "Integration created: $INTEGRATION_ID"

# Create route for POST /validate
echo "Creating route POST /validate..."
awslocal apigatewayv2 create-route \
    --api-id $API_ID \
    --route-key "POST /validate" \
    --target "integrations/$INTEGRATION_ID"

# Create default stage with auto-deploy
echo "Creating stage..."
awslocal apigatewayv2 create-stage \
    --api-id $API_ID \
    --stage-name '$default' \
    --auto-deploy

# Build the API endpoint
API_ENDPOINT="http://localhost:4566/restapis/$API_ID/\$default/_user_request_"

echo ""
echo "========================================"
echo "API Gateway configured for Order Validation!"
echo "========================================"
echo ""
echo "API ID: $API_ID"
echo ""
echo "Test endpoint:"
echo ""
echo "POST /validate:"
echo "  curl -X POST '$API_ENDPOINT/validate' \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":2,\"totalPrice\":100}'"
echo ""
