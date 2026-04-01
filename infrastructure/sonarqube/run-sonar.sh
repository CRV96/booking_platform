#!/bin/bash

set -e

# Ensure Java 21 is used
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."
cd "$PROJECT_ROOT"

echo "=========================================="
echo "  Running SonarQube Analysis"
echo "=========================================="
echo "Project root: $PROJECT_ROOT"
echo ""

# Requires SONAR_TOKEN to be set in your environment.
# Source your .env file first: export $(grep -v '^#' .env | grep -v '^$' | xargs)
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=Booking-Application \
  -Dsonar.projectName='Booking-Application' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token="${SONAR_TOKEN}"

echo ""
echo "=========================================="
echo "  Analysis Complete!"
echo "=========================================="
echo "View results at: http://localhost:9000/dashboard?id=Booking"
