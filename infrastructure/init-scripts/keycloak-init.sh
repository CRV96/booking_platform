#!/bin/bash
# =============================================================================
# Keycloak Utility Script - Booking Platform
# =============================================================================
#
# This script provides utilities for managing Keycloak after initialization.
# The realm is automatically imported on first startup via --import-realm flag.
#
# Prerequisites:
#   - jq (JSON processor)
#   - curl
#
# Usage:
#   ./keycloak-init.sh [command]
#
# =============================================================================

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM_NAME="booking-platform"

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

log_info()  { echo -e "\033[0;32m[INFO]\033[0m $1"; }
log_warn()  { echo -e "\033[1;33m[WARN]\033[0m $1"; }
log_error() { echo -e "\033[0;31m[ERROR]\033[0m $1"; }

check_dependencies() {
    command -v jq >/dev/null 2>&1 || { log_error "jq is required but not installed."; exit 1; }
    command -v curl >/dev/null 2>&1 || { log_error "curl is required but not installed."; exit 1; }
}

# -----------------------------------------------------------------------------
# Keycloak API Functions
# -----------------------------------------------------------------------------

wait_for_keycloak() {
    log_info "Waiting for Keycloak to be ready at ${KEYCLOAK_URL}..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "${KEYCLOAK_URL}/health/ready" >/dev/null 2>&1; then
            log_info "Keycloak is ready!"
            return 0
        fi
        log_info "Attempt $attempt/$max_attempts - waiting..."
        sleep 5
        ((attempt++))
    done

    log_error "Keycloak did not become ready"
    exit 1
}

get_admin_token() {
    local token
    token=$(curl -sf -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=${KEYCLOAK_ADMIN}" \
        -d "password=${KEYCLOAK_ADMIN_PASSWORD}" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" | jq -r '.access_token')

    if [ -z "$token" ] || [ "$token" = "null" ]; then
        log_error "Failed to get admin token. Check credentials."
        exit 1
    fi
    echo "$token"
}

# -----------------------------------------------------------------------------
# Commands
# -----------------------------------------------------------------------------

cmd_status() {
    check_dependencies
    wait_for_keycloak

    local token
    token=$(get_admin_token)

    echo ""
    echo "=============================================="
    echo "  Keycloak Status - ${REALM_NAME}"
    echo "=============================================="
    echo ""
    echo "URL: ${KEYCLOAK_URL}"
    echo "Admin Console: ${KEYCLOAK_URL}/admin"
    echo ""

    # Check if realm exists
    local realm_check
    realm_check=$(curl -sf -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer ${token}" \
        "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}")

    if [ "$realm_check" = "200" ]; then
        echo "Realm '${REALM_NAME}': EXISTS"

        # Users count
        local users_count
        users_count=$(curl -sf -H "Authorization: Bearer ${token}" \
            "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users/count")
        echo "Users: ${users_count}"

        # Roles
        echo ""
        echo "Realm Roles:"
        curl -sf -H "Authorization: Bearer ${token}" \
            "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/roles" \
            | jq -r '.[] | "  - \(.name): \(.description // "No description")"'

        # Clients (filter system clients)
        echo ""
        echo "Custom Clients:"
        curl -sf -H "Authorization: Bearer ${token}" \
            "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients" \
            | jq -r '.[] | select(.clientId | test("^(account|admin|broker|realm|security)") | not) | "  - \(.clientId) (public: \(.publicClient))"'
    else
        echo "Realm '${REALM_NAME}': NOT FOUND"
    fi
    echo ""
}

cmd_export() {
    check_dependencies
    wait_for_keycloak

    local token
    token=$(get_admin_token)

    local output_file="../keycloak/${REALM_NAME}-export-$(date +%Y%m%d-%H%M%S).json"

    log_info "Exporting realm '${REALM_NAME}'..."
    curl -sf -H "Authorization: Bearer ${token}" \
        "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}" \
        | jq '.' > "$output_file"

    log_info "Exported to: $output_file"
}

cmd_create_user() {
    local username=$1
    local email=$2
    local password=$3
    local role=${4:-customer}

    if [ -z "$username" ] || [ -z "$email" ] || [ -z "$password" ]; then
        log_error "Usage: $0 create-user <username> <email> <password> [role]"
        exit 1
    fi

    check_dependencies
    wait_for_keycloak

    local token
    token=$(get_admin_token)

    log_info "Creating user: ${username} with role: ${role}"

    # Create user
    curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json" \
        -d "{
            \"username\": \"${username}\",
            \"email\": \"${email}\",
            \"emailVerified\": true,
            \"enabled\": true,
            \"credentials\": [{
                \"type\": \"password\",
                \"value\": \"${password}\",
                \"temporary\": false
            }]
        }"

    # Get user ID
    local user_id
    user_id=$(curl -sf -H "Authorization: Bearer ${token}" \
        "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users?username=${username}" \
        | jq -r '.[0].id')

    # Assign role
    local role_data
    role_data=$(curl -sf -H "Authorization: Bearer ${token}" \
        "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/roles/${role}")

    curl -sf -X POST "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/users/${user_id}/role-mappings/realm" \
        -H "Authorization: Bearer ${token}" \
        -H "Content-Type: application/json" \
        -d "[${role_data}]"

    log_info "User '${username}' created successfully"
}

cmd_get_secret() {
    local client_id=$1

    if [ -z "$client_id" ]; then
        log_error "Usage: $0 get-secret <client-id>"
        exit 1
    fi

    check_dependencies
    wait_for_keycloak

    local token
    token=$(get_admin_token)

    # Get internal client UUID
    local internal_id
    internal_id=$(curl -sf -H "Authorization: Bearer ${token}" \
        "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients?clientId=${client_id}" \
        | jq -r '.[0].id')

    if [ -z "$internal_id" ] || [ "$internal_id" = "null" ]; then
        log_error "Client '${client_id}' not found"
        exit 1
    fi

    # Get secret
    local secret
    secret=$(curl -sf -H "Authorization: Bearer ${token}" \
        "${KEYCLOAK_URL}/admin/realms/${REALM_NAME}/clients/${internal_id}/client-secret" \
        | jq -r '.value')

    echo "$secret"
}

cmd_help() {
    echo "Keycloak Utility Script - Booking Platform"
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  status                                 Show Keycloak and realm status"
    echo "  export                                 Export realm to JSON file"
    echo "  create-user <user> <email> <pw> [role] Create a new user (default role: customer)"
    echo "  get-secret <client-id>                 Get client secret"
    echo "  wait                                   Wait for Keycloak to be ready"
    echo "  help                                   Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  KEYCLOAK_URL            Keycloak URL (default: http://localhost:8180)"
    echo "  KEYCLOAK_ADMIN          Admin username (default: admin)"
    echo "  KEYCLOAK_ADMIN_PASSWORD Admin password (default: admin)"
    echo ""
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

case "${1:-help}" in
    status)     cmd_status ;;
    export)     cmd_export ;;
    create-user) cmd_create_user "$2" "$3" "$4" "$5" ;;
    get-secret) cmd_get_secret "$2" ;;
    wait)       check_dependencies && wait_for_keycloak ;;
    help|*)     cmd_help ;;
esac
