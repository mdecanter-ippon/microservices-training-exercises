#!/bin/bash

# Export OpenAPI specifications from running services
# This script fetches the OpenAPI specs and saves them as YAML files

set -e

OPENAPI_DIR="../openapi"
mkdir -p "$OPENAPI_DIR"

echo "Exporting OpenAPI specifications..."

# Check if services are running
check_service() {
    local service=$1
    local port=$2
    if ! curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
        echo "Error: ${service} is not running on port ${port}"
        echo "Please start the service with: mvn spring-boot:run -pl ${service}"
        return 1
    fi
}

# Export user-service spec
if check_service "user-service" 8081; then
    echo "Exporting user-service specification..."
    curl -s http://localhost:8081/v3/api-docs.yaml > "$OPENAPI_DIR/user-service.yaml"
    echo "✓ user-service.yaml exported"
fi

# Export shipment-service spec
if check_service "shipment-service" 8082; then
    echo "Exporting shipment-service specification..."
    curl -s http://localhost:8082/v3/api-docs.yaml > "$OPENAPI_DIR/shipment-service.yaml"
    echo "✓ shipment-service.yaml exported"
fi

# Export order-service spec
if check_service "order-service" 8083; then
    echo "Exporting order-service specification..."
    curl -s http://localhost:8083/v3/api-docs.yaml > "$OPENAPI_DIR/order-service.yaml"
    echo "✓ order-service.yaml exported"
fi

echo ""
echo "OpenAPI specifications exported to $OPENAPI_DIR/"
echo ""
echo "Next steps:"
echo "  1. Review the specifications in $OPENAPI_DIR/"
echo "  2. Generate client SDKs with: ./generate-clients.sh"
echo "  3. View Swagger UI at:"
echo "     - http://localhost:8081/swagger-ui.html (user-service)"
echo "     - http://localhost:8082/swagger-ui.html (shipment-service)"
echo "     - http://localhost:8083/swagger-ui.html (order-service)"
