#!/bin/bash
set -e

FUNCTION_NAME="order-validation"
JAR_PATH="../lambda-service/target/lambda-service-1.0.0-SNAPSHOT-aws.jar"
HANDLER="org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"

echo "=== Deploying Order Validation Lambda ==="

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: JAR not found at $JAR_PATH"
    echo "Run 'mvn clean package -DskipTests' in lambda-service first"
    exit 1
fi

# Delete existing function (ignore error if doesn't exist)
echo "Cleaning up existing function..."
awslocal lambda delete-function --function-name $FUNCTION_NAME 2>/dev/null || true

# Create IAM role for Lambda (if doesn't exist)
echo "Creating IAM role..."
awslocal iam create-role \
    --role-name lambda-role \
    --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}' \
    2>/dev/null || true

# Create the Lambda function
echo "Creating Lambda function: $FUNCTION_NAME"
awslocal lambda create-function \
    --function-name $FUNCTION_NAME \
    --runtime java21 \
    --handler $HANDLER \
    --role arn:aws:iam::000000000000:role/lambda-role \
    --zip-file fileb://$JAR_PATH \
    --timeout 30 \
    --memory-size 512 \
    --environment "Variables={SPRING_CLOUD_FUNCTION_DEFINITION=orderValidationFunction}"

# Wait for function to be active
echo "Waiting for function to be ready..."
sleep 5

# Verify function is created
echo ""
echo "========================================"
echo "Order Validation Lambda deployed!"
echo "========================================"
echo ""
echo "Test with:"
echo "  awslocal lambda invoke --function-name $FUNCTION_NAME \\"
echo "    --payload '{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":2,\"totalPrice\":100}' \\"
echo "    --cli-binary-format raw-in-base64-out response.json && cat response.json"
echo ""

# List functions
awslocal lambda list-functions --query 'Functions[].{Name:FunctionName,Runtime:Runtime,Memory:MemorySize}'
