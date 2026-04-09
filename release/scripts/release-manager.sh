#!/bin/sh
# =============================================================================
# release-manager.sh
#
# Applies incremental release changes (Keycloak partial imports) for versions
# between current-version and target-version defined in release.yaml.
#
# Behavior:
#   - Fresh install (current-version: none):
#       Keycloak realm is already imported via --import-realm from init/keycloak/.
#       Flyway applies SQL migrations on service startup.
#       This script just records the target version and exits.
#
#   - Upgrade (current-version is set):
#       Applies Keycloak partial imports for each version in range
#       (current, target] in order. SQL is left to Flyway.
#
# Usage: run automatically as a Docker init container (see docker-compose).
# =============================================================================
set -eu

RELEASE_DIR="/release"
CONFIG_FILE="$RELEASE_DIR/release.yaml"

log()  { echo "[release-manager] $1"; }
info() { log "INFO  $1"; }
warn() { log "WARN  $1"; }
err()  { log "ERROR $1"; }

# ── Config parsing ────────────────────────────────────────────────────────────

get_config() {
    grep "^$1:" "$CONFIG_FILE" | sed 's/^[^:]*:[[:space:]]*//' | tr -d '"'"'" | tr -d ' '
}

set_config() {
    sed -i "s|^$1:.*|$1: $2|" "$CONFIG_FILE"
}

# ── Version helpers ───────────────────────────────────────────────────────────

# Returns all release version directories in ascending order.
all_versions() {
    ls -d "$RELEASE_DIR"/v*.*.* 2>/dev/null | xargs -I{} basename {} | sort
}

# Returns 0 if $1 comes strictly before $2 in sorted order.
version_before() {
    FIRST=$(printf '%s\n%s' "$1" "$2" | sort | head -1)
    [ "$FIRST" = "$1" ] && [ "$1" != "$2" ]
}

# ── Keycloak helpers ──────────────────────────────────────────────────────────

wait_for_keycloak() {
    info "Waiting for Keycloak to be ready..."
    RETRIES=60
    while [ "$RETRIES" -gt 0 ]; do
        if curl -sf "http://bkg-keycloak:9000/health/ready" > /dev/null 2>&1; then
            info "Keycloak is ready."
            return 0
        fi
        RETRIES=$((RETRIES - 1))
        sleep 5
    done
    err "Keycloak did not become ready within 5 minutes."
    return 1
}

get_admin_token() {
    TOKEN=$(curl -sf \
        -X POST "http://bkg-keycloak:8080/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "client_id=admin-cli&username=${KC_ADMIN_USER:-admin}&password=${KC_ADMIN_PASSWORD:-admin}&grant_type=password" \
        2>/dev/null | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

    if [ -z "$TOKEN" ]; then
        err "Failed to obtain Keycloak admin token. Check KC_ADMIN_USER / KC_ADMIN_PASSWORD."
        return 1
    fi
    echo "$TOKEN"
}

apply_keycloak_for_version() {
    VERSION=$1
    KC_DIR="$RELEASE_DIR/$VERSION/keycloak"

    if [ ! -d "$KC_DIR" ]; then
        info "  [$VERSION] No Keycloak changes — skipping."
        return 0
    fi

    TOKEN=$(get_admin_token)

    for JSON_FILE in "$KC_DIR"/*.json; do
        [ -f "$JSON_FILE" ] || continue
        FILENAME=$(basename "$JSON_FILE")

        # Files named realm-*.json are applied as realm-level updates (PUT).
        # All other *.json files are applied via partialImport.
        case "$FILENAME" in
            realm-*.json)
                info "  [$VERSION] Applying realm update from $FILENAME..."

                HTTP_STATUS=$(curl -s -o /tmp/kc_response.txt -w "%{http_code}" \
                    -X PUT "http://bkg-keycloak:8080/admin/realms/booking-platform" \
                    -H "Authorization: Bearer $TOKEN" \
                    -H "Content-Type: application/json" \
                    -d @"$JSON_FILE" 2>/dev/null)

                RESPONSE=$(cat /tmp/kc_response.txt 2>/dev/null || echo "")

                if [ "$HTTP_STATUS" = "204" ]; then
                    info "  [$VERSION] $FILENAME applied successfully."
                else
                    warn "  [$VERSION] $FILENAME update returned HTTP $HTTP_STATUS: $RESPONSE"
                fi
                ;;
            *)
                info "  [$VERSION] Importing $FILENAME..."

                HTTP_STATUS=$(curl -s -o /tmp/kc_response.txt -w "%{http_code}" \
                    -X POST "http://bkg-keycloak:8080/admin/realms/booking-platform/partialImport" \
                    -H "Authorization: Bearer $TOKEN" \
                    -H "Content-Type: application/json" \
                    -d @"$JSON_FILE" 2>/dev/null)

                RESPONSE=$(cat /tmp/kc_response.txt 2>/dev/null || echo "")

                if [ "$HTTP_STATUS" = "200" ]; then
                    info "  [$VERSION] $FILENAME imported successfully. Response: $RESPONSE"
                else
                    warn "  [$VERSION] $FILENAME import returned HTTP $HTTP_STATUS: $RESPONSE"
                fi
                ;;
        esac
    done
}

# ── Main ──────────────────────────────────────────────────────────────────────

CURRENT=$(get_config "current-version")
TARGET=$(get_config "target-version")

info "Current version: $CURRENT"
info "Target version:  $TARGET"

if [ "$CURRENT" = "$TARGET" ]; then
    info "Already at $TARGET — nothing to do."
    exit 0
fi

# ── Fresh install ─────────────────────────────────────────────────────────────
# Keycloak realm is imported via --import-realm from init/keycloak/.
# Flyway handles all SQL migrations on service startup.
# Nothing for this script to do except record the version.

if [ "$CURRENT" = "none" ]; then
    info "Fresh install detected."
    info "Keycloak: realm imported via --import-realm from init/keycloak/."
    info "SQL: Flyway will apply all migrations on service startup."
    set_config "current-version" "$TARGET"
    info "State recorded as $TARGET."
    exit 0
fi

# ── Upgrade ───────────────────────────────────────────────────────────────────
# Collect versions between current (exclusive) and target (inclusive).

VERSIONS_TO_APPLY=""
PAST_CURRENT=false

for V in $(all_versions); do
    if [ "$PAST_CURRENT" = "true" ]; then
        VERSIONS_TO_APPLY="$VERSIONS_TO_APPLY $V"
    fi
    if [ "$V" = "$CURRENT" ]; then
        PAST_CURRENT=true
    fi
    if [ "$V" = "$TARGET" ]; then
        break
    fi
done

VERSIONS_TO_APPLY=$(echo "$VERSIONS_TO_APPLY" | sed 's/^ *//' | sed 's/ *$//')

if [ -z "$VERSIONS_TO_APPLY" ]; then
    warn "No release directories found between $CURRENT and $TARGET."
    warn "Available versions: $(all_versions | tr '\n' ' ')"
    exit 0
fi

info "Versions to apply: $VERSIONS_TO_APPLY"

wait_for_keycloak

for V in $VERSIONS_TO_APPLY; do
    info "--- Applying $V ---"
    apply_keycloak_for_version "$V"
    info "--- $V done ---"
done

set_config "current-version" "$TARGET"
info "Successfully upgraded from $CURRENT to $TARGET."
