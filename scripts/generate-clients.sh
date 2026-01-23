#!/bin/bash

# Generate client SDKs from OpenAPI specifications
# Supports Java, TypeScript, and other languages

set -e

OPENAPI_DIR="../openapi"
GENERATED_DIR="../generated-clients"

# Check if OpenAPI Generator CLI is installed
if ! command -v openapi-generator-cli &> /dev/null; then
    echo "OpenAPI Generator CLI not found."
    echo "Installing via npm..."
    npm install -g @openapitools/openapi-generator-cli
fi

mkdir -p "$GENERATED_DIR"

echo "Generating client SDKs from OpenAPI specifications..."

# Function to generate a client
generate_client() {
    local service=$1
    local language=$2
    local spec_file="$OPENAPI_DIR/${service}.yaml"
    local output_dir="$GENERATED_DIR/${language}/${service}-client"

    if [ ! -f "$spec_file" ]; then
        echo "Warning: ${spec_file} not found. Run export-openapi-specs.sh first."
        return 1
    fi

    echo "Generating ${language} client for ${service}..."
    openapi-generator-cli generate \
        -i "$spec_file" \
        -g "$language" \
        -o "$output_dir" \
        --additional-properties=apiPackage=com.dornach.${service}.client.api,modelPackage=com.dornach.${service}.client.model

    echo "✓ ${language} client generated at ${output_dir}"
}

# Generate Java clients
echo ""
echo "=== Generating Java Clients ==="
generate_client "user-service" "java"
generate_client "shipment-service" "java"
generate_client "order-service" "java"

# Generate TypeScript clients (for frontend)
echo ""
echo "=== Generating TypeScript Clients ==="
generate_client "user-service" "typescript-axios"
generate_client "shipment-service" "typescript-axios"
generate_client "order-service" "typescript-axios"

echo ""
echo "Client SDKs generated successfully in $GENERATED_DIR/"
echo ""
echo "Example usage (Java):"
echo "  ApiClient client = new ApiClient();"
echo "  client.setBasePath(\"http://localhost:8081\");"
echo "  UsersApi api = new UsersApi(client);"
echo "  List<UserResponse> users = api.getAllUsers();"
