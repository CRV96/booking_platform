#!/bin/bash
# Installs all infrastructure Helm releases into the booking-platform namespace.
# Run this once before deploying services: ./helm/install.sh
# Uses --wait so each release is ready before the next one starts.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NAMESPACE=booking-platform

echo "Adding Helm repositories..."
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

echo ""
echo "Installing PostgreSQL..."
helm upgrade --install postgres bitnami/postgresql \
  --namespace "$NAMESPACE" \
  --values "$SCRIPT_DIR/postgres/values.yaml" \
  --wait

echo ""
echo "Installing MongoDB..."
helm upgrade --install mongodb bitnami/mongodb \
  --namespace "$NAMESPACE" \
  --values "$SCRIPT_DIR/mongodb/values.yaml" \
  --wait

echo ""
echo "Installing Redis..."
helm upgrade --install redis bitnami/redis \
  --namespace "$NAMESPACE" \
  --values "$SCRIPT_DIR/redis/values.yaml" \
  --wait

echo ""
echo "Installing Kafka..."
helm upgrade --install kafka bitnami/kafka \
  --namespace "$NAMESPACE" \
  --values "$SCRIPT_DIR/kafka/values.yaml" \
  --wait

echo ""
echo "Installing Keycloak (depends on Postgres being ready)..."
helm upgrade --install keycloak bitnami/keycloak \
  --namespace "$NAMESPACE" \
  --values "$SCRIPT_DIR/keycloak/values.yaml" \
  --wait

echo ""
echo "All infrastructure ready."
helm list --namespace "$NAMESPACE"
