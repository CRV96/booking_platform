#!/bin/bash
# =============================================================================
# Generate mTLS certificates for service-to-service communication
# =============================================================================
#
# This script generates:
#   1. Root CA (Certificate Authority) - signs all other certificates
#   2. Server certificate for user-service (gRPC server)
#   3. Client certificate for graphql-gateway (gRPC client)
#
# Usage: ./generate-certs.sh
#
# Output files:
#   ca.crt, ca.key           - Root CA (keep ca.key secure!)
#   user-service.crt/key     - Server certificate
#   graphql-gateway.crt/key  - Client certificate
#
# =============================================================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
CERT_DIR="$(cd "$(dirname "$0")" && pwd)"
CA_SUBJECT="/C=US/ST=State/L=City/O=BookingPlatform/OU=Dev/CN=BookingPlatformCA"
DAYS_VALID=365
KEY_SIZE=4096

# Service names (used as CN in certificates)
SERVICES=("user-service" "graphql-gateway" "event-service" "booking-service")

echo -e "${CYAN}=============================================${NC}"
echo -e "${CYAN}  mTLS Certificate Generator${NC}"
echo -e "${CYAN}=============================================${NC}"
echo ""

cd "$CERT_DIR"

# =============================================================================
# Step 1: Generate Root CA
# =============================================================================
echo -e "${YELLOW}[1/3] Generating Root CA...${NC}"

if [ -f "ca.crt" ] && [ -f "ca.key" ]; then
    echo -e "${YELLOW}  CA already exists. Skipping...${NC}"
    echo -e "${YELLOW}  (Delete ca.crt and ca.key to regenerate)${NC}"
else
    # Generate CA private key
    openssl genrsa -out ca.key $KEY_SIZE 2>/dev/null

    # Generate CA certificate
    openssl req -new -x509 -days $DAYS_VALID -key ca.key -out ca.crt \
        -subj "$CA_SUBJECT" 2>/dev/null

    echo -e "${GREEN}  ✓ Generated ca.key and ca.crt${NC}"
fi

# =============================================================================
# Step 2: Generate Server/Client Certificates
# =============================================================================
echo -e "${YELLOW}[2/3] Generating service certificates...${NC}"

generate_cert() {
    local SERVICE_NAME=$1
    local IS_SERVER=$2  # "server" or "client"

    if [ -f "${SERVICE_NAME}.crt" ] && [ -f "${SERVICE_NAME}.key" ]; then
        echo -e "${YELLOW}  ${SERVICE_NAME}: Already exists. Skipping...${NC}"
        return
    fi

    # Generate private key
    openssl genrsa -out "${SERVICE_NAME}.key" $KEY_SIZE 2>/dev/null

    # Create certificate signing request (CSR)
    openssl req -new -key "${SERVICE_NAME}.key" -out "${SERVICE_NAME}.csr" \
        -subj "/C=US/ST=State/L=City/O=BookingPlatform/OU=${SERVICE_NAME}/CN=${SERVICE_NAME}" 2>/dev/null

    # Create extensions file for SAN (Subject Alternative Name)
    cat > "${SERVICE_NAME}.ext" << EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, keyEncipherment
EOF

    if [ "$IS_SERVER" = "server" ]; then
        cat >> "${SERVICE_NAME}.ext" << EOF
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = ${SERVICE_NAME}
DNS.2 = localhost
DNS.3 = *.${SERVICE_NAME}
IP.1 = 127.0.0.1
IP.2 = 0.0.0.0
EOF
    else
        cat >> "${SERVICE_NAME}.ext" << EOF
extendedKeyUsage = clientAuth
EOF
    fi

    # Sign the certificate with CA
    openssl x509 -req -in "${SERVICE_NAME}.csr" -CA ca.crt -CAkey ca.key \
        -CAcreateserial -out "${SERVICE_NAME}.crt" -days $DAYS_VALID \
        -extfile "${SERVICE_NAME}.ext" 2>/dev/null

    # Cleanup
    rm -f "${SERVICE_NAME}.csr" "${SERVICE_NAME}.ext"

    echo -e "${GREEN}  ✓ Generated ${SERVICE_NAME}.key and ${SERVICE_NAME}.crt${NC}"
}

# Generate server certificates (for gRPC servers)
generate_cert "user-service" "server"
generate_cert "event-service" "server"
generate_cert "booking-service" "server"

# Generate client certificates (for gRPC clients)
generate_cert "graphql-gateway" "client"

# =============================================================================
# Step 3: Copy to service resources
# =============================================================================
echo -e "${YELLOW}[3/3] Copying certificates to services...${NC}"

SERVICES_DIR="$CERT_DIR/../../services"

copy_certs_to_service() {
    local SERVICE_NAME=$1
    local CERT_TYPE=$2  # "server" or "client"
    local TARGET_DIR="$SERVICES_DIR/$SERVICE_NAME/src/main/resources/certs"

    mkdir -p "$TARGET_DIR"

    # Always copy CA cert (for verification)
    cp ca.crt "$TARGET_DIR/"

    # Copy service-specific certs
    cp "${SERVICE_NAME}.crt" "$TARGET_DIR/"
    cp "${SERVICE_NAME}.key" "$TARGET_DIR/"

    echo -e "${GREEN}  ✓ Copied certs to $SERVICE_NAME/src/main/resources/certs/${NC}"
}

copy_certs_to_service "user-service" "server"
copy_certs_to_service "event-service" "server"
copy_certs_to_service "graphql-gateway" "client"

# =============================================================================
# Summary
# =============================================================================
echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}  Certificate Generation Complete!${NC}"
echo -e "${GREEN}=============================================${NC}"
echo ""
echo -e "Generated files in ${CYAN}$CERT_DIR${NC}:"
echo ""
ls -la *.crt *.key 2>/dev/null | awk '{print "  " $9 " (" $5 " bytes)"}'
echo ""
echo -e "${YELLOW}Important:${NC}"
echo -e "  - Keep ${RED}ca.key${NC} secure (it can sign new certificates)"
echo -e "  - Certificates are valid for ${CYAN}$DAYS_VALID days${NC}"
echo -e "  - Regenerate by deleting files and re-running this script"
echo ""
echo -e "${CYAN}Next steps:${NC}"
echo -e "  1. Restart user-service and graphql-gateway"
echo -e "  2. Verify mTLS is working with the test endpoints"
echo ""
