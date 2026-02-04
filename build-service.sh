#!/bin/bash
# =============================================================================
# Build a specific module or all modules
# Usage: ./build-service.sh <module-name> [options]
# Example: ./build-service.sh user-service
# Example: ./build-service.sh user-service --with-deps
# Example: ./build-service.sh all
# ./build-service.sh user-service --clean      # Clean and build
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Set Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

show_usage() {
    echo "Usage: ./build-service.sh <module-name> [options]"
    echo ""
    echo "Modules:"
    echo "  common              - Shared gRPC definitions and utilities"
    echo "  config-service      - Configuration server"
    echo "  eureka-service      - Service discovery"
    echo "  graphql-gateway     - GraphQL API gateway"
    echo "  user-service        - User management"
    echo "  event-service       - Event management"
    echo "  booking-service     - Booking management"
    echo "  payment-service     - Payment processing"
    echo "  ticket-service      - Ticket management"
    echo "  notification-service - Notifications"
    echo "  analytics-service   - Analytics"
    echo "  all                 - Build all services (excludes config-service and eureka-service)"
    echo "  all-with-infra      - Build all services including config-service and eureka-service"
    echo ""
    echo "Options:"
    echo "  --with-deps, -d     Also build dependencies (mvn -am)"
    echo "  --clean, -c         Clean before building"
    echo "  --tests, -t         Run tests (default: skip tests)"
    echo "  --quick, -q         Quick build (skip tests, skip javadoc)"
    echo "  --help, -h          Show this help"
    echo ""
    echo "Examples:"
    echo "  ./build-service.sh user-service              # Build user-service only"
    echo "  ./build-service.sh user-service --with-deps  # Build with dependencies"
    echo "  ./build-service.sh user-service --clean      # Clean and build"
    echo "  ./build-service.sh all                       # Build all app services (no infra)"
    echo "  ./build-service.sh all-with-infra            # Build everything including infra"
}

if [ -z "$1" ] || [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
    show_usage
    exit 0
fi

MODULE_NAME=$1
shift

# Parse options
WITH_DEPS=false
CLEAN=false
SKIP_TESTS=true
QUICK=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --with-deps|-d)
            WITH_DEPS=true
            shift
            ;;
        --clean|-c)
            CLEAN=true
            shift
            ;;
        --tests|-t)
            SKIP_TESTS=false
            shift
            ;;
        --quick|-q)
            QUICK=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_usage
            exit 1
            ;;
    esac
done

# Services lists
INFRA_SERVICES="services/config-service,services/eureka-service"
APP_SERVICES="common,services/graphql-gateway,services/user-service,services/event-service,services/booking-service,services/payment-service,services/ticket-service,services/notification-service,services/analytics-service"

# Determine module path
case $MODULE_NAME in
    common)
        MODULE_PATH="common"
        ;;
    config-service|eureka-service|graphql-gateway|user-service|event-service|booking-service|payment-service|ticket-service|notification-service|analytics-service)
        MODULE_PATH="services/$MODULE_NAME"
        ;;
    all)
        # Build all except infrastructure services (config-service, eureka-service)
        MODULE_PATH="$APP_SERVICES"
        ;;
    all-with-infra)
        # Build everything including infrastructure services
        MODULE_PATH=""
        ;;
    *)
        echo -e "${RED}Unknown module: $MODULE_NAME${NC}"
        show_usage
        exit 1
        ;;
esac

# Build Maven command
MVN_CMD="mvn"

if [ "$CLEAN" = true ]; then
    MVN_CMD="$MVN_CMD clean"
fi

MVN_CMD="$MVN_CMD install"

if [ -n "$MODULE_PATH" ]; then
    MVN_CMD="$MVN_CMD -pl $MODULE_PATH"
fi

if [ "$WITH_DEPS" = true ] && [ -n "$MODULE_PATH" ]; then
    MVN_CMD="$MVN_CMD -am"
fi

if [ "$SKIP_TESTS" = true ]; then
    MVN_CMD="$MVN_CMD -DskipTests"
fi

if [ "$QUICK" = true ]; then
    MVN_CMD="$MVN_CMD -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true"
fi

# Print build info
echo -e "${YELLOW}=======================================${NC}"
echo -e "${YELLOW}Building: ${GREEN}$MODULE_NAME${NC}"
echo -e "${YELLOW}Command:  ${NC}$MVN_CMD"
echo -e "${YELLOW}Java:     ${NC}$JAVA_HOME"
echo -e "${YELLOW}=======================================${NC}"
echo ""

# Execute build
$MVN_CMD

# Print result
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}=======================================${NC}"
    echo -e "${GREEN}Build successful: $MODULE_NAME${NC}"
    echo -e "${GREEN}=======================================${NC}"
else
    echo ""
    echo -e "${RED}=======================================${NC}"
    echo -e "${RED}Build failed: $MODULE_NAME${NC}"
    echo -e "${RED}=======================================${NC}"
    exit 1
fi
