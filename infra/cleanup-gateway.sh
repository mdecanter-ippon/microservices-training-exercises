#!/bin/bash

# Cleanup LocalStack API Gateway

set -e

LOCALSTACK_ENDPOINT=${LOCALSTACK_ENDPOINT:-http://localhost:4566}

if [ -z "$DORNACH_API_ID" ]; then
    echo "❌ DORNACH_API_ID not set"
    echo "   List APIs with: awslocal apigatewayv2 get-apis"
    echo "   Then: export DORNACH_API_ID=<your-api-id>"
    exit 1
fi

echo "🧹 Cleaning up API Gateway: $DORNACH_API_ID"

awslocal apigatewayv2 delete-api --api-id "$DORNACH_API_ID"

echo "✅ API Gateway deleted"
