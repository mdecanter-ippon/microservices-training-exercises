# API Gateway v2 (HTTP API) Configuration
resource "aws_apigatewayv2_api" "dornach_gateway" {
  name          = "dornach-gateway"
  protocol_type = "HTTP"

  # Optional: Add CORS configuration
  cors_configuration {
    allow_headers = ["content-type", "authorization"]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"]
    allow_origins = ["*"]
    max_age       = 300
  }
}

# Integration for User Service
resource "aws_apigatewayv2_integration" "user_integration" {
  api_id           = aws_apigatewayv2_api.dornach_gateway.id
  integration_type = "HTTP_PROXY"

  integration_uri      = "http://user-service:8081/{proxy}"
  integration_method   = "ANY"
  connection_type      = "INTERNET"
}

# Integration for Shipment Service
resource "aws_apigatewayv2_integration" "shipment_integration" {
  api_id           = aws_apigatewayv2_api.dornach_gateway.id
  integration_type = "HTTP_PROXY"

  integration_uri      = "http://shipment-service:8082/{proxy}"
  integration_method   = "ANY"
  connection_type      = "INTERNET"
}

# Integration for Order Service
resource "aws_apigatewayv2_integration" "order_integration" {
  api_id           = aws_apigatewayv2_api.dornach_gateway.id
  integration_type = "HTTP_PROXY"

  integration_uri      = "http://order-service:8083/{proxy}"
  integration_method   = "ANY"
  connection_type      = "INTERNET"
}

# Route for User Service - matches ANY method to /users/{proxy+}
resource "aws_apigatewayv2_route" "user_route" {
  api_id    = aws_apigatewayv2_api.dornach_gateway.id
  route_key = "ANY /users/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.user_integration.id}"
}

# Route for Shipment Service - matches ANY method to /shipments/{proxy+}
resource "aws_apigatewayv2_route" "shipment_route" {
  api_id    = aws_apigatewayv2_api.dornach_gateway.id
  route_key = "ANY /shipments/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.shipment_integration.id}"
}

# Route for Order Service - matches ANY method to /orders/{proxy+}
resource "aws_apigatewayv2_route" "order_route" {
  api_id    = aws_apigatewayv2_api.dornach_gateway.id
  route_key = "ANY /orders/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.order_integration.id}"
}

# Production Stage
resource "aws_apigatewayv2_stage" "prod" {
  api_id      = aws_apigatewayv2_api.dornach_gateway.id
  name        = "prod"
  auto_deploy = true

  # Optional: Add access logging
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway_logs.arn
    format          = jsonencode({
      requestId               = "$context.requestId"
      sourceIp                = "$context.identity.sourceIp"
      caller                  = "$context.identity.caller"
      user                    = "$context.identity.user"
      requestTime             = "$context.requestTime"
      httpMethod              = "$context.httpMethod"
      resourcePath            = "$context.resourcePath"
      status                  = "$context.status"
      protocol                = "$context.protocol"
      responseLength          = "$context.responseLength"
    })
  }
}

# CloudWatch Log Group for API Gateway logs
resource "aws_cloudwatch_log_group" "api_gateway_logs" {
  name = "/aws/api-gateway/dornach-gateway"
}
