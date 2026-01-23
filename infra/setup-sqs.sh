#!/bin/bash

# =============================================================================
# Bonus Step B - SQS Setup Script
# Creates order-events queue with dead letter queue in LocalStack
# =============================================================================

set -e

LOCALSTACK_URL=${LOCALSTACK_URL:-http://localhost:4566}
AWS_REGION=${AWS_REGION:-us-east-1}

echo "=============================================="
echo "Setting up SQS Queues in LocalStack"
echo "=============================================="
echo "LocalStack URL: $LOCALSTACK_URL"
echo "Region: $AWS_REGION"
echo ""

# Function to run AWS CLI commands against LocalStack
awslocal() {
    aws --endpoint-url=$LOCALSTACK_URL --region=$AWS_REGION "$@"
}

# Wait for LocalStack to be ready
echo "Waiting for LocalStack to be ready..."
max_attempts=30
attempt=0
while ! awslocal sqs list-queues > /dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
        echo "ERROR: LocalStack is not responding after $max_attempts attempts"
        exit 1
    fi
    echo "  Attempt $attempt/$max_attempts - waiting..."
    sleep 2
done
echo "LocalStack is ready!"
echo ""

# Step 1: Create Dead Letter Queue first
echo "Step 1: Creating Dead Letter Queue (order-events-dlq)..."
DLQ_URL=$(awslocal sqs create-queue \
    --queue-name order-events-dlq \
    --attributes '{"MessageRetentionPeriod":"1209600"}' \
    --query 'QueueUrl' \
    --output text 2>/dev/null || true)

if [ -z "$DLQ_URL" ]; then
    DLQ_URL=$(awslocal sqs get-queue-url --queue-name order-events-dlq --query 'QueueUrl' --output text)
    echo "  DLQ already exists: $DLQ_URL"
else
    echo "  DLQ created: $DLQ_URL"
fi

# Get DLQ ARN for redrive policy
DLQ_ARN=$(awslocal sqs get-queue-attributes \
    --queue-url "$DLQ_URL" \
    --attribute-names QueueArn \
    --query 'Attributes.QueueArn' \
    --output text)
echo "  DLQ ARN: $DLQ_ARN"
echo ""

# Step 2: Create main queue with redrive policy
echo "Step 2: Creating main queue (order-events) with redrive policy..."
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"3\"}"

QUEUE_URL=$(awslocal sqs create-queue \
    --queue-name order-events \
    --attributes "{\"RedrivePolicy\":\"$(echo $REDRIVE_POLICY | sed 's/"/\\"/g')\",\"VisibilityTimeout\":\"30\"}" \
    --query 'QueueUrl' \
    --output text 2>/dev/null || true)

if [ -z "$QUEUE_URL" ]; then
    QUEUE_URL=$(awslocal sqs get-queue-url --queue-name order-events --query 'QueueUrl' --output text)
    # Update attributes if queue exists
    awslocal sqs set-queue-attributes \
        --queue-url "$QUEUE_URL" \
        --attributes "{\"RedrivePolicy\":\"$(echo $REDRIVE_POLICY | sed 's/"/\\"/g')\"}"
    echo "  Queue already exists, updated redrive policy: $QUEUE_URL"
else
    echo "  Queue created: $QUEUE_URL"
fi
echo ""

# Step 3: Verify setup
echo "Step 3: Verifying SQS setup..."
echo ""
echo "Listing all queues:"
awslocal sqs list-queues --query 'QueueUrls' --output table
echo ""

# Get main queue attributes
echo "Main queue attributes (order-events):"
awslocal sqs get-queue-attributes \
    --queue-url "$QUEUE_URL" \
    --attribute-names All \
    --query 'Attributes.{VisibilityTimeout:VisibilityTimeout,RedrivePolicy:RedrivePolicy}' \
    --output table
echo ""

echo "=============================================="
echo "SQS Setup Complete!"
echo "=============================================="
echo ""
echo "Queue URLs:"
echo "  - Main Queue:        $QUEUE_URL"
echo "  - Dead Letter Queue: $DLQ_URL"
echo ""
echo "Configuration:"
echo "  - maxReceiveCount: 3 (messages move to DLQ after 3 failed attempts)"
echo "  - VisibilityTimeout: 30 seconds"
echo ""
echo "Test commands:"
echo "  # Send a test message:"
echo "  awslocal sqs send-message --queue-url $QUEUE_URL --message-body '{\"test\":\"message\"}'"
echo ""
echo "  # Receive messages:"
echo "  awslocal sqs receive-message --queue-url $QUEUE_URL"
echo ""
