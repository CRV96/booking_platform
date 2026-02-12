#!/bin/bash
# =============================================================================
# Generate mTLS certificates for service-to-service communication
# =============================================================================
#
# This script generates:
#   1. Root CA (Certificate Authority) - signs all other certificates
#   2. Server certificates for all gRPC server services
#   3. Client certificate for graphql-gateway (gRPC client)
#
# Usage: ./generate-certs.sh
#
# Output files (in infrastructure/certs/):
#   ca.crt, ca.key               - Root CA (keep ca.key secure!)
#   user-service.crt/key         - gRPC server (port 9091)
#   event-service.crt/key        - gRPC server (port 9093)
#   booking-service.crt/key      - gRPC server (port 9094)
#   payment-service.crt/key      - gRPC server (port 9095)
#   ticket-service.crt/key       - gRPC server (port 9096)
#   analytics-service.crt/key    - gRPC server (port 9097)
#   graphql-gateway.crt/key      - gRPC client
#
# Certificates are also copied to each service's src/main/resources/certs/
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
    openssl genrsa -out ca.key $KEY_SIZE 2>/dev/null
    openssl req -new -x509 -days $DAYS_VALID -key ca.key -out ca.crt \
        -subj "$CA_SUBJECT" 2>/dev/null
    echo -e "${GREEN}  ✓ Generated ca.key and ca.crt${NC}"
fi

# =============================================================================
# Step 2: Generate service certificates
# =============================================================================
echo -e "${YELLOW}[2/3] Generating service certificates...${NC}"

generate_cert() {
    local SERVICE_NAME=$1
    local IS_SERVER=$2  # "server" or "client"

    if [ -f "${SERVICE_NAME}.crt" ] && [ -f "${SERVICE_NAME}.key" ]; then
        echo -e "${YELLOW}  ${SERVICE_NAME}: Already exists. Skipping...${NC}"
        return
    fi

    openssl genrsa -out "${SERVICE_NAME}.key" $KEY_SIZE 2>/dev/null

    openssl req -new -key "${SERVICE_NAME}.key" -out "${SERVICE_NAME}.csr" \
        -subj "/C=US/ST=State/L=City/O=BookingPlatform/OU=${SERVICE_NAME}/CN=${SERVICE_NAME}" 2>/dev/null

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

    openssl x509 -req -in "${SERVICE_NAME}.csr" -CA ca.crt -CAkey ca.key \
        -CAcreateserial -out "${SERVICE_NAME}.crt" -days $DAYS_VALID \
        -extfile "${SERVICE_NAME}.ext" 2>/dev/null

    rm -f "${SERVICE_NAME}.csr" "${SERVICE_NAME}.ext"
    echo -e "${GREEN}  ✓ Generated ${SERVICE_NAME}.key and ${SERVICE_NAME}.crt${NC}"
}

# gRPC server certificates
generate_cert "user-service"      "server"
generate_cert "event-service"     "server"
generate_cert "booking-service"   "server"
generate_cert "payment-service"   "server"
generate_cert "ticket-service"    "server"
generate_cert "analytics-service" "server"

# gRPC client certificates
generate_cert "graphql-gateway"   "client"

# =============================================================================
# Step 3: Copy certificates to each service's resources
# =============================================================================
echo -e "${YELLOW}[3/3] Copying certificates to services...${NC}"

SERVICES_DIR="$CERT_DIR/../../services"

copy_certs_to_service() {
    local SERVICE_NAME=$1
    local TARGET_DIR="$SERVICES_DIR/$SERVICE_NAME/src/main/resources/certs"

    mkdir -p "$TARGET_DIR"

    cp ca.crt "$TARGET_DIR/"
    cp "${SERVICE_NAME}.crt" "$TARGET_DIR/"
    cp "${SERVICE_NAME}.key" "$TARGET_DIR/"

    # Create .gitignore to prevent committing private keys
    if [ ! -f "$TARGET_DIR/.gitignore" ]; then
        cat > "$TARGET_DIR/.gitignore" << 'GITIGNORE'
# Ignore certificates (copied by infrastructure/certs/generate-certs.sh)
*.crt
*.key
*.pem
GITIGNORE
    fi

    echo -e "${GREEN}  ✓ Copied to $SERVICE_NAME/src/main/resources/certs/${NC}"
}

# gRPC servers
copy_certs_to_service "user-service"
copy_certs_to_service "event-service"
copy_certs_to_service "booking-service"
copy_certs_to_service "payment-service"
copy_certs_to_service "ticket-service"
copy_certs_to_service "analytics-service"

# gRPC client
copy_certs_to_service "graphql-gateway"

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
echo -e "  - Regenerate by deleting *.crt/*.key and re-running this script"
echo ""
echo -e "${CYAN}Next steps:${NC}"
echo -e "  1. Restart affected services"
echo -e "  2. Verify mTLS is working with grpcurl or actuator health"
echo ""
