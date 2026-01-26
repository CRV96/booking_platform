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

# Run Maven with SonarQube using 1Password to inject env vars
op run --env-file=".env" -- mvn clean verify sonar:sonar \
  -Dsonar.projectKey=Booking-Platform \
  -Dsonar.projectName='Booking Platform' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token="${SONAR_QUBE_TOKEN}"

# Run without 1Password, replace the "${SONAR_QUBE_TOKEN}" with the one from the SonarQube portal and uncomment the below block and comment the above one
# mvn clean verify sonar:sonar \
#  -Dsonar.projectKey=Booking-Platform \
#  -Dsonar.projectName='Booking Platform' \
#  -Dsonar.host.url=http://localhost:9000 \
#  -Dsonar.token="${SONAR_QUBE_TOKEN}"

echo ""
echo "=========================================="
echo "  Analysis Complete!"
echo "=========================================="
echo "View results at: http://localhost:9000/dashboard?id=Booking-Platform"
