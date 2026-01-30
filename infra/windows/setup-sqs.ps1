# =============================================================================
# Bonus Step B - SQS Setup Script
# Creates order-events queue with dead letter queue in LocalStack
# =============================================================================

$ErrorActionPreference = "Stop"

$LOCALSTACK_URL = if ($env:LOCALSTACK_URL) { $env:LOCALSTACK_URL } else { "http://localhost:4566" }
$AWS_REGION = if ($env:AWS_REGION) { $env:AWS_REGION } else { "us-east-1" }

# Set dummy AWS credentials for LocalStack
$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = $AWS_REGION

Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "Setting up SQS Queues in LocalStack"
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "LocalStack URL: $LOCALSTACK_URL"
Write-Host "Region: $AWS_REGION"
Write-Host ""

# Wait for LocalStack to be ready
Write-Host "Waiting for LocalStack to be ready..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 0

while ($true) {
    try {
        $result = aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs list-queues 2>$null
        if ($LASTEXITCODE -eq 0) { break }
    } catch {}

    $attempt++
    if ($attempt -ge $maxAttempts) {
        Write-Host "ERROR: LocalStack is not responding after $maxAttempts attempts" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Attempt $attempt/$maxAttempts - waiting..."
    Start-Sleep -Seconds 2
}
Write-Host "LocalStack is ready!" -ForegroundColor Green
Write-Host ""

# Step 1: Create Dead Letter Queue first
Write-Host "Step 1: Creating Dead Letter Queue (order-events-dlq)..." -ForegroundColor Yellow

$DLQ_URL = aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs create-queue `
    --queue-name order-events-dlq `
    --attributes '{\"MessageRetentionPeriod\":\"1209600\"}' `
    --query 'QueueUrl' --output text 2>$null

if (-not $DLQ_URL -or $DLQ_URL -eq "None") {
    $DLQ_URL = aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs get-queue-url `
        --queue-name order-events-dlq --query 'QueueUrl' --output text
    Write-Host "  DLQ already exists: $DLQ_URL" -ForegroundColor White
} else {
    Write-Host "  DLQ created: $DLQ_URL" -ForegroundColor Green
}

# Get DLQ ARN for redrive policy
$DLQ_ARN = aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs get-queue-attributes `
    --queue-url $DLQ_URL `
    --attribute-names QueueArn `
    --query 'Attributes.QueueArn' --output text

Write-Host "  DLQ ARN: $DLQ_ARN" -ForegroundColor White
Write-Host ""

# Step 2: Create main queue with redrive policy
Write-Host "Step 2: Creating main queue (order-events) with redrive policy..." -ForegroundColor Yellow

$REDRIVE_POLICY = "{`"deadLetterTargetArn`":`"$DLQ_ARN`",`"maxReceiveCount`":`"3`"}"
$ESCAPED_POLICY = $REDRIVE_POLICY.Replace('"', '\"')
$ATTRIBUTES = "{`"RedrivePolicy`":`"$ESCAPED_POLICY`",`"VisibilityTimeout`":`"30`"}"

$QUEUE_URL = aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs create-queue `
    --queue-name order-events `
    --attributes $ATTRIBUTES `
    --query 'QueueUrl' --output text 2>$null

if (-not $QUEUE_URL -or $QUEUE_URL -eq "None") {
    $QUEUE_URL = aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs get-queue-url `
        --queue-name order-events --query 'QueueUrl' --output text

    # Update attributes if queue exists
    aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs set-queue-attributes `
        --queue-url $QUEUE_URL `
        --attributes "{`"RedrivePolicy`":`"$ESCAPED_POLICY`"}" | Out-Null

    Write-Host "  Queue already exists, updated redrive policy: $QUEUE_URL" -ForegroundColor White
} else {
    Write-Host "  Queue created: $QUEUE_URL" -ForegroundColor Green
}
Write-Host ""

# Step 3: Verify setup
Write-Host "Step 3: Verifying SQS setup..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Listing all queues:" -ForegroundColor White
aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs list-queues --query 'QueueUrls' --output table
Write-Host ""

# Get main queue attributes
Write-Host "Main queue attributes (order-events):" -ForegroundColor White
aws --endpoint-url $LOCALSTACK_URL --region $AWS_REGION sqs get-queue-attributes `
    --queue-url $QUEUE_URL `
    --attribute-names All `
    --query 'Attributes.{VisibilityTimeout:VisibilityTimeout,RedrivePolicy:RedrivePolicy}' `
    --output table
Write-Host ""

Write-Host "==============================================" -ForegroundColor Green
Write-Host "SQS Setup Complete!"
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""
Write-Host "Queue URLs:" -ForegroundColor Cyan
Write-Host "  - Main Queue:        $QUEUE_URL"
Write-Host "  - Dead Letter Queue: $DLQ_URL"
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Cyan
Write-Host "  - maxReceiveCount: 3 (messages move to DLQ after 3 failed attempts)"
Write-Host "  - VisibilityTimeout: 30 seconds"
Write-Host ""
Write-Host "Test commands:" -ForegroundColor Cyan
Write-Host "  # Send a test message:"
Write-Host "  aws --endpoint-url $LOCALSTACK_URL sqs send-message --queue-url $QUEUE_URL --message-body '{`"test`":`"message`"}'"
Write-Host ""
Write-Host "  # Receive messages:"
Write-Host "  aws --endpoint-url $LOCALSTACK_URL sqs receive-message --queue-url $QUEUE_URL"
Write-Host ""
