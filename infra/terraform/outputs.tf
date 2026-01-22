# API Gateway Outputs
output "api_gateway_id" {
  description = "ID of the created API Gateway"
  value       = aws_apigatewayv2_api.dornach_gateway.id
}

output "api_gateway_name" {
  description = "Name of the created API Gateway"
  value       = aws_apigatewayv2_api.dornach_gateway.name
}

output "api_gateway_endpoint" {
  description = "Endpoint URL of the API Gateway"
  value       = aws_apigatewayv2_api.dornach_gateway.api_endpoint
}

output "gateway_url" {
  description = "Full gateway URL with stage for direct access"
  value       = "${aws_apigatewayv2_api.dornach_gateway.api_endpoint}/${aws_apigatewayv2_stage.prod.name}"
}

output "stage_name" {
  description = "Name of the deployed stage"
  value       = aws_apigatewayv2_stage.prod.name
}

output "log_group_name" {
  description = "Name of the CloudWatch log group for API Gateway logs"
  value       = aws_cloudwatch_log_group.api_gateway_logs.name
}

output "user_service_route" {
  description = "Route configuration for user service"
  value       = aws_apigatewayv2_route.user_route.route_key
}

output "shipment_service_route" {
  description = "Route configuration for shipment service"
  value       = aws_apigatewayv2_route.shipment_route.route_key
}

output "order_service_route" {
  description = "Route configuration for order service"
  value       = aws_apigatewayv2_route.order_route.route_key
}
