# API Gateway Configuration Variables
variable "api_gateway_name" {
  description = "Name of the API Gateway"
  type        = string
  default     = "dornach-gateway"
}

variable "stage_name" {
  description = "Name of the deployment stage"
  type        = string
  default     = "prod"
}

variable "user_service_url" {
  description = "URL of the user service"
  type        = string
  default     = "http://user-service:8081/{proxy}"
}

variable "shipment_service_url" {
  description = "URL of the shipment service"
  type        = string
  default     = "http://shipment-service:8082/{proxy}"
}

variable "order_service_url" {
  description = "URL of the order service"
  type        = string
  default     = "http://order-service:8083/{proxy}"
}

variable "cors_allow_origins" {
  description = "Allowed origins for CORS"
  type        = list(string)
  default     = ["*"]
}

variable "cors_allow_methods" {
  description = "Allowed HTTP methods for CORS"
  type        = list(string)
  default     = ["GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"]
}

variable "cors_allow_headers" {
  description = "Allowed headers for CORS"
  type        = list(string)
  default     = ["content-type", "authorization"]
}

variable "cors_max_age" {
  description = "Max age for CORS preflight responses"
  type        = number
  default     = 300
}

variable "log_group_name" {
  description = "Name of the CloudWatch log group"
  type        = string
  default     = "/aws/api-gateway/dornach-gateway"
}
