#!/bin/bash
# Loads .env.k8s and applies all secret.yaml files with envsubst.
# Run this once before deploying: ./apply-secrets.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.k8s"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE — copy .env.k8s.example and fill in your values"
  exit 1
fi

export $(grep -v '^#' "$ENV_FILE" | xargs)

find "$SCRIPT_DIR/services" -name "secret.yaml" | while read -r file; do
  echo "Applying $file"
  envsubst < "$file" | kubectl apply -f -
done

echo "All secrets applied."
