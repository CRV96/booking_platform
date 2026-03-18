#!/usr/bin/env bash
# =============================================================================
# Run a specific Spring Boot service
# Usage: ./run-service.sh <service-name> [options]
# Example: ./run-service.sh config-service
# Example: ./run-service.sh user-service --debug
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Valid services
VALID_SERVICES=(
    "config-service"
    "eureka-service"
    "graphql-gateway"
    "user-service"
    "event-service"
    "booking-service"
    "payment-service"
    "ticket-service"
    "notification-service"
    "analytics-service"
)

# Get debug port for a service (unique ports for simultaneous debugging)
get_debug_port() {
    case "$1" in
        config-service)       echo 5005 ;;
        eureka-service)       echo 5006 ;;
        graphql-gateway)      echo 5007 ;;
        user-service)         echo 5008 ;;
        event-service)        echo 5009 ;;
        booking-service)      echo 5010 ;;
        payment-service)      echo 5011 ;;
        ticket-service)       echo 5012 ;;
        notification-service) echo 5013 ;;
        analytics-service)    echo 5014 ;;
        *)                    echo 5005 ;;
    esac
}

show_usage() {
    echo "Usage: ./run-service.sh <service-name> [options]"
    echo ""
    echo "Available services (with default debug ports):"
    for service in "${VALID_SERVICES[@]}"; do
        port=$(get_debug_port "$service")
        echo "  $service (port $port)"
    done
    echo ""
    echo "Options:"
    echo "  --debug, -d          Enable remote debugging (uses service-specific port)"
    echo "  --port, -p <port>    Custom debug port (overrides default)"
    echo "  --suspend, -s        Wait for debugger before starting"
    echo "  --help, -h           Show this help"
    echo ""
    echo "Examples:"
    echo "  ./run-service.sh user-service              # Run normally"
    echo "  ./run-service.sh user-service --debug      # Run with debug on port 5008"
    echo "  ./run-service.sh graphql-gateway -d        # Run with debug on port 5007"
    echo "  ./run-service.sh user-service -d -p 5099   # Run with debug on custom port"
    echo "  ./run-service.sh user-service -d -s        # Debug & wait for debugger"
}

is_valid_service() {
    local service=$1
    for valid in "${VALID_SERVICES[@]}"; do
        if [ "$valid" == "$service" ]; then
            return 0
        fi
    done
    return 1
}

if [ -z "$1" ] || [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    show_usage
    exit 0
fi

SERVICE_NAME=$1
shift

# Validate service name
if ! is_valid_service "$SERVICE_NAME"; then
    echo -e "${RED}Unknown service: $SERVICE_NAME${NC}"
    show_usage
    exit 1
fi

# Parse options
DEBUG_MODE=false
DEBUG_PORT=$(get_debug_port "$SERVICE_NAME")
SUSPEND="n"

while [[ $# -gt 0 ]]; do
    case $1 in
        --debug|-d)
            DEBUG_MODE=true
            shift
            ;;
        --port|-p)
            DEBUG_PORT="$2"
            shift 2
            ;;
        --suspend|-s)
            SUSPEND="y"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_usage
            exit 1
            ;;
    esac
done

# Set Java home
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Build Maven command
MVN_CMD="mvn spring-boot:run -pl services/$SERVICE_NAME"

# Add debug configuration if enabled
if [ "$DEBUG_MODE" = true ]; then
    DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$SUSPEND,address=*:$DEBUG_PORT"
    MVN_CMD="$MVN_CMD -Dspring-boot.run.jvmArguments=\"$DEBUG_OPTS\""

    echo -e "${YELLOW}=======================================${NC}"
    echo -e "${YELLOW}DEBUG MODE ENABLED${NC}"
    echo -e "${YELLOW}=======================================${NC}"
    echo -e "Service:      ${GREEN}$SERVICE_NAME${NC}"
    echo -e "Debug Port:   ${CYAN}$DEBUG_PORT${NC}"
    echo -e "Suspend:      ${CYAN}$SUSPEND${NC}"
    echo -e "${YELLOW}=======================================${NC}"

    if [ "$SUSPEND" = "y" ]; then
        echo -e "${YELLOW}Waiting for debugger to attach on port $DEBUG_PORT...${NC}"
    else
        echo -e "${GREEN}Attach debugger to port $DEBUG_PORT when ready${NC}"
    fi
    echo ""
else
    echo -e "${GREEN}Starting $SERVICE_NAME...${NC}"
fi

# ─── Run mode ───────────────────────────────────────────────────────────────
# Default: run directly using exported environment variables.
# To use 1Password CLI for secret injection, uncomment the "op run" line
# and comment out the plain "eval" line below.
# ────────────────────────────────────────────────────────────────────────────

# Plain mode (default) — uses environment variables from your shell
eval "$MVN_CMD"

# 1Password mode (optional) — injects secrets from .env via 1Password CLI
op run --env-file=".env" -- bash -c "$MVN_CMD"
