#!/bin/bash
# =============================================================================
# Run a specific Spring Boot service
# Usage: ./run-service.sh <service-name>
# Example: ./run-service.sh config-service
# =============================================================================

set -e

if [ -z "$1" ]; then
    echo "Usage: ./run-service.sh <service-name>"
    echo ""
    echo "Available services:"
    echo "  config-service"
    echo "  eureka-service"
    echo "  graphql-gateway"
    echo "  user-service"
    echo "  event-service"
    echo "  booking-service"
    echo "  payment-service"
    echo "  ticket-service"
    echo "  notification-service"
    echo "  analytics-service"
    exit 1
fi

SERVICE_NAME=$1
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

echo "Starting $SERVICE_NAME..."
op run --env-file=".env" -- mvn spring-boot:run -pl "services/$SERVICE_NAME"
