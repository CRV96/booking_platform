#!/bin/bash
# =============================================================================
# Build a specific module or all modules
# Usage: ./build-service.sh <module-name> [options]
# Example: ./build-service.sh user-service
# Example: ./build-service.sh user-service --with-deps
# Example: ./build-service.sh all
# Example: ./build-service.sh all --clean --tests
# ./build-service.sh user-service --clean      # Clean and build
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Set Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Services that have integration tests — extend this list as new test suites are added
TESTABLE_SERVICES="services/user-service services/event-service services/payment-service"

show_usage() {
    echo "Usage: ./build-service.sh <module-name> [options]"
    echo ""
    echo "Modules:"
    echo "  common              - All common modules (proto, core, security)"
    echo "  common-proto        - Shared protobuf definitions and gRPC stubs"
    echo "  common-core         - Shared base classes and utilities"
    echo "  common-security     - Shared server security infrastructure"
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
    echo "                      With 'all': builds everything, then runs tests per service"
    echo "  --quick, -q         Quick build (skip tests, skip javadoc)"
    echo "  --help, -h          Show this help"
    echo ""
    echo "Examples:"
    echo "  ./build-service.sh user-service              # Build user-service only"
    echo "  ./build-service.sh user-service --with-deps  # Build with dependencies"
    echo "  ./build-service.sh user-service --clean      # Clean and build"
    echo "  ./build-service.sh user-service --tests      # Build and run tests"
    echo "  ./build-service.sh common                    # Build all common modules"
    echo "  ./build-service.sh common-proto              # Build only proto stubs"
    echo "  ./build-service.sh all                       # Build all app services (no infra)"
    echo "  ./build-service.sh all --clean               # Clean and build all app services"
    echo "  ./build-service.sh all --clean --tests       # Build all + run integration tests"
    echo "  ./build-service.sh all-with-infra            # Build everything including infra"
    echo ""
    echo "Services with integration tests: $TESTABLE_SERVICES"
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

# Common sub-modules
COMMON_MODULES="common/common-proto,common/common-core,common/common-security"

# Services lists
INFRA_SERVICES="services/config-service,services/eureka-service"
APP_SERVICES="$COMMON_MODULES,services/graphql-gateway,services/user-service,services/event-service,services/booking-service,services/payment-service,services/ticket-service,services/notification-service,services/analytics-service"

# Determine module path
case $MODULE_NAME in
    common)
        MODULE_PATH="$COMMON_MODULES"
        ;;
    common-proto)
        MODULE_PATH="common/common-proto"
        ;;
    common-core)
        MODULE_PATH="common/common-core"
        ;;
    common-security)
        MODULE_PATH="common/common-security"
        ;;
    config-service|eureka-service|graphql-gateway|user-service|event-service|booking-service|payment-service|ticket-service|notification-service|analytics-service)
        MODULE_PATH="services/$MODULE_NAME"
        ;;
    all)
        MODULE_PATH="$APP_SERVICES"
        ;;
    all-with-infra)
        MODULE_PATH=""
        ;;
    *)
        echo -e "${RED}Unknown module: $MODULE_NAME${NC}"
        show_usage
        exit 1
        ;;
esac

# =============================================================================
# Helper: print a section banner
# =============================================================================
print_banner() {
    local title="$1"
    local color="${2:-$YELLOW}"
    echo ""
    echo -e "${color}${BOLD}=======================================${NC}"
    echo -e "${color}${BOLD}  $title${NC}"
    echo -e "${color}${BOLD}=======================================${NC}"
    echo ""
}

# =============================================================================
# Helper: run a single Maven command and print its result
# =============================================================================
run_mvn() {
    local label="$1"
    local cmd="$2"

    echo -e "${CYAN}Command:  ${NC}$cmd"
    echo -e "${CYAN}Java:     ${NC}$JAVA_HOME"
    echo ""

    if eval "$cmd"; then
        echo ""
        echo -e "${GREEN}${BOLD}✔  $label — PASSED${NC}"
        return 0
    else
        echo ""
        echo -e "${RED}${BOLD}✘  $label — FAILED${NC}"
        return 1
    fi
}

# =============================================================================
# BUILD + TEST: "all" or "all-with-infra" with --tests
#
# Strategy:
#   1. Build the entire project skipping tests (fast, validates compilation)
#   2. Run tests per-service (only services in TESTABLE_SERVICES), one at a time
#      so failures are isolated and each service gets its own clear result line
# =============================================================================
if [[ ("$MODULE_NAME" == "all" || "$MODULE_NAME" == "all-with-infra") && "$SKIP_TESTS" == "false" ]]; then

    # --- Phase 1: full build, tests skipped ---
    print_banner "Phase 1/2 — Build all modules (tests skipped)" "$YELLOW"

    BUILD_CMD="mvn"
    [ "$CLEAN" = true ] && BUILD_CMD="$BUILD_CMD clean"
    BUILD_CMD="$BUILD_CMD install -DskipTests"
    [ "$QUICK" = true ] && BUILD_CMD="$BUILD_CMD -Dmaven.javadoc.skip=true -Dmaven.source.skip=true"
    [ -n "$MODULE_PATH" ] && BUILD_CMD="$BUILD_CMD -pl $MODULE_PATH"

    if ! run_mvn "Full build" "$BUILD_CMD"; then
        print_banner "Build failed — tests not run" "$RED"
        exit 1
    fi

    # --- Phase 2: run tests per testable service ---
    print_banner "Phase 2/2 — Integration tests" "$BLUE"

    PASSED=()
    FAILED=()

    for SERVICE_PATH in $TESTABLE_SERVICES; do
        SERVICE_NAME=$(basename "$SERVICE_PATH")

        echo -e "${YELLOW}--- Testing: ${GREEN}${BOLD}$SERVICE_NAME${NC}${YELLOW} ---${NC}"

        TEST_CMD="mvn test -pl $SERVICE_PATH"

        if run_mvn "$SERVICE_NAME tests" "$TEST_CMD"; then
            PASSED+=("$SERVICE_NAME")
        else
            FAILED+=("$SERVICE_NAME")
        fi
        echo ""
    done

    # --- Summary ---
    print_banner "Test Results Summary" "$BOLD"

    echo -e "  Services built:  ${GREEN}$MODULE_NAME${NC}"
    echo -e "  Services tested: ${CYAN}$(echo $TESTABLE_SERVICES | tr ' ' '\n' | xargs -I{} basename {} | tr '\n' ' ')${NC}"
    echo ""

    if [ ${#PASSED[@]} -gt 0 ]; then
        for s in "${PASSED[@]}"; do
            echo -e "  ${GREEN}${BOLD}✔  $s${NC}"
        done
    fi

    if [ ${#FAILED[@]} -gt 0 ]; then
        for s in "${FAILED[@]}"; do
            echo -e "  ${RED}${BOLD}✘  $s${NC}"
        done
        echo ""
        echo -e "${RED}${BOLD}Some tests failed. See output above for details.${NC}"
        echo ""
        exit 1
    fi

    echo ""
    echo -e "${GREEN}${BOLD}All tests passed.${NC}"
    echo ""
    exit 0
fi

# =============================================================================
# STANDARD FLOW: single service or "all" without --tests
# =============================================================================

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
print_banner "Building: $MODULE_NAME" "$YELLOW"

if ! run_mvn "$MODULE_NAME" "$MVN_CMD"; then
    print_banner "Build failed: $MODULE_NAME" "$RED"
    exit 1
fi

print_banner "Build successful: $MODULE_NAME" "$GREEN"
