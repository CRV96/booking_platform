#!/bin/bash
# Starts the booking platform in Kubernetes.
# Fresh start  → installs everything from scratch.
# Subsequent   → skips healthy Helm releases, re-applies manifests.
#
# Usage (from project root): ./infrastructure/k8s/run.sh

set -e

# ─── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }
step()    { echo -e "\n${CYAN}━━━ $* ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; }

# ─── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
K8S_DIR="$PROJECT_ROOT/infrastructure/k8s"
NS=booking-platform

# ─── Prerequisites ────────────────────────────────────────────────────────────
step "Checking prerequisites"
command -v kubectl &>/dev/null || error "kubectl not found"
command -v helm    &>/dev/null || error "helm not found"
kubectl cluster-info &>/dev/null || error "Kubernetes cluster not reachable"
success "kubectl, helm OK"

# ─── Detect kind cluster ──────────────────────────────────────────────────────
CONTEXT=$(kubectl config current-context)
IS_KIND=false
KIND_CLUSTER=""
if [[ "$CONTEXT" == kind-* ]]; then
  if command -v kind &>/dev/null; then
    IS_KIND=true
    KIND_CLUSTER="${CONTEXT#kind-}"
    info "kind cluster detected: $KIND_CLUSTER"
  else
    warn "kind context detected but 'kind' CLI not found — images will not be loaded into kind"
  fi
else
  info "cluster context: $CONTEXT"
fi

# ─── Load env file ────────────────────────────────────────────────────────────
step "Loading environment variables"
ENV_FILE="$K8S_DIR/.env.k8s"
[ -f "$ENV_FILE" ] || error "Missing $ENV_FILE — fill in your values"
set -a && source "$ENV_FILE" && set +a
success "Loaded $ENV_FILE"

# ─── Namespace ────────────────────────────────────────────────────────────────
step "Namespace"
kubectl apply -f "$K8S_DIR/namespace.yaml"
success "Namespace $NS ready"

# ─── Build service images ─────────────────────────────────────────────────────
step "Building service images"
SERVICES=(
  config-service
  eureka-service
  user-service
  event-service
  booking-service
  payment-service
  notification-service
  analytics-service
  ticket-service
  graphql-gateway
)
for svc in "${SERVICES[@]}"; do
  info "Building $svc..."
  docker build -f "$PROJECT_ROOT/infrastructure/docker/Dockerfile.service" \
    --build-arg SERVICE_NAME="$svc" \
    -t "booking-platform/$svc:latest" \
    "$PROJECT_ROOT" -q
  success "$svc image built"
done

# ─── Load images into kind ────────────────────────────────────────────────────
if [ "$IS_KIND" = true ]; then
  step "Loading images into kind cluster ($KIND_CLUSTER)"
  for svc in "${SERVICES[@]}"; do
    info "Loading booking-platform/$svc:latest..."
    kind load docker-image "booking-platform/$svc:latest" --name "$KIND_CLUSTER"
    success "booking-platform/$svc:latest loaded"
  done
fi

# ─── Spring Cloud Config files ────────────────────────────────────────────────
# Mounted at /config/dev — config-server searches at ${CONFIG_SERVER_CONFIGURATIONS_PATH}/{profile}
step "Spring Cloud Config files"
kubectl create configmap spring-config-files \
  --from-file="$PROJECT_ROOT/config/dev/" \
  --namespace "$NS" \
  --dry-run=client -o yaml | kubectl apply -f -
success "spring-config-files ConfigMap applied"

# ─── Secrets (kubectl --from-literal avoids envsubst subshell issues on macOS) ─
step "Secrets"

kubectl create secret generic user-service-secret \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --from-literal=DB_POSTGRES_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --from-literal=USER_SERVICE_KEYCLOAK_CLIENT_SECRET="$USER_SERVICE_KEYCLOAK_CLIENT_SECRET" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic booking-service-secret \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --from-literal=DB_POSTGRES_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic payment-service-secret \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --from-literal=DB_POSTGRES_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --from-literal=STRIPE_SECRET_KEY="$STRIPE_SECRET_KEY" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic event-service-secret \
  --from-literal=DB_MONGO_USERNAME="$DB_MONGO_USERNAME" \
  --from-literal=DB_MONGO_PASSWORD="$DB_MONGO_PASSWORD" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic analytics-service-secret \
  --from-literal=DB_MONGO_USERNAME="$DB_MONGO_USERNAME" \
  --from-literal=DB_MONGO_PASSWORD="$DB_MONGO_PASSWORD" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic ticket-service-secret \
  --from-literal=DB_MONGO_USERNAME="$DB_MONGO_USERNAME" \
  --from-literal=DB_MONGO_PASSWORD="$DB_MONGO_PASSWORD" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic notification-service-secret \
  --from-literal=NOTIFICATION_SERVICE_KEYCLOAK_CLIENT_SECRET="$NOTIFICATION_SERVICE_KEYCLOAK_CLIENT_SECRET" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic keycloak-secret \
  --from-literal=KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  --from-literal=KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  --from-literal=KC_DB_USERNAME=postgres \
  --from-literal=KC_DB_PASSWORD="$DB_POSTGRES_PASSWORD" \
  --namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

success "All secrets applied"

# ─── Common ConfigMap ─────────────────────────────────────────────────────────
step "Common ConfigMap"
kubectl apply -f "$K8S_DIR/common/configmap.yaml"
success "common-config applied"

# ─── Helper: apply service files (skips secret.yaml — secrets created above) ──
apply_service() {
  local dir="$K8S_DIR/services/$1"
  kubectl apply -f "$dir/configmap.yaml" 2>/dev/null || true
  kubectl apply -f "$dir/deployment.yaml"
  kubectl apply -f "$dir/service.yaml"
}

# ─── Helper: wait for deployment to be ready ──────────────────────────────────
wait_for() {
  local name=$1
  local timeout=${2:-300s}
  info "Waiting for $name..."
  kubectl rollout status deployment/"$name" --namespace "$NS" --timeout="$timeout"
  success "$name ready"
}

# ─── Helm helper ──────────────────────────────────────────────────────────────
helm_install() {
  local name=$1
  local chart=$2
  local values=$3
  local timeout=${4:-5m}

  local status
  status=$(helm status "$name" --namespace "$NS" 2>/dev/null | grep "STATUS:" | awk '{print $2}')

  if [ "$status" = "deployed" ]; then
    info "$name already deployed — skipping"
    return
  fi

  if [ -n "$status" ]; then
    warn "$name exists with status '$status' — uninstalling and reinstalling"
    helm uninstall "$name" --namespace "$NS" --wait
  fi

  info "Installing $name..."
  helm upgrade --install "$name" "$chart" \
    --namespace "$NS" \
    --values "$values" \
    --wait --timeout "$timeout"
  success "$name ready"
}

# ─── Helm repositories ────────────────────────────────────────────────────────
step "Helm repositories"
helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true
helm repo update
success "Helm repos updated"

# ─── ingress-nginx ────────────────────────────────────────────────────────────
step "ingress-nginx"
if ! kubectl get ingressclass nginx &>/dev/null; then
  info "Installing ingress-nginx..."
  kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml
  kubectl wait --namespace ingress-nginx \
    --for=condition=ready pod \
    --selector=app.kubernetes.io/component=controller \
    --timeout=120s
  success "ingress-nginx installed and ready"
else
  info "ingress-nginx already installed — skipping"
fi

# ─── Helm infrastructure ──────────────────────────────────────────────────────
step "Helm — PostgreSQL"
helm_install postgres bitnami/postgresql "$K8S_DIR/helm/postgres/values.yaml"

step "Helm — MongoDB"
helm_install mongodb bitnami/mongodb "$K8S_DIR/helm/mongodb/values.yaml"

step "Helm — Redis"
helm_install redis bitnami/redis "$K8S_DIR/helm/redis/values.yaml"

step "Kafka"
kubectl apply -f "$K8S_DIR/infrastructure/kafka/"
kubectl rollout status deployment/kafka --namespace "$NS" --timeout=180s
success "Kafka ready"

step "Keycloak"
kubectl apply -f "$K8S_DIR/infrastructure/keycloak/deployment.yaml"
kubectl apply -f "$K8S_DIR/infrastructure/keycloak/service.yaml"
kubectl rollout status deployment/keycloak --namespace "$NS" --timeout=300s
success "Keycloak ready"

# ─── Simple infrastructure ────────────────────────────────────────────────────
step "Infrastructure — Zipkin + Mailhog"
kubectl apply -f "$K8S_DIR/infrastructure/zipkin/"
kubectl apply -f "$K8S_DIR/infrastructure/mailhog/"
kubectl rollout status deployment/zipkin  --namespace "$NS" --timeout=60s
kubectl rollout status deployment/mailhog --namespace "$NS" --timeout=60s
success "Zipkin and Mailhog ready"

# ─── Services ─────────────────────────────────────────────────────────────────
step "config-service"
apply_service config-service
wait_for config-service 300s

step "eureka-service"
apply_service eureka-service
wait_for eureka-service 300s

step "user-service + event-service"
apply_service user-service
apply_service event-service
wait_for user-service 300s
wait_for event-service 300s

step "booking-service + payment-service + analytics-service + ticket-service"
apply_service booking-service
apply_service payment-service
apply_service analytics-service
apply_service ticket-service
wait_for booking-service  300s
wait_for payment-service  300s
wait_for analytics-service 300s
wait_for ticket-service   300s

step "notification-service"
apply_service notification-service
wait_for notification-service 300s

step "graphql-gateway"
apply_service graphql-gateway
wait_for graphql-gateway 300s

# ─── Ingress ──────────────────────────────────────────────────────────────────
step "Ingress"
kubectl apply -f "$K8S_DIR/ingress/"
success "Ingress applied"

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  Booking Platform is running${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  GraphQL endpoint : http://booking.local/graphql"
echo "  GraphiQL UI      : http://booking.local/graphiql"
echo "  Zipkin           : kubectl port-forward svc/zipkin 9411:9411 -n $NS"
echo "  Mailhog UI       : kubectl port-forward svc/mailhog 8025:8025 -n $NS"
echo "  Keycloak         : kubectl port-forward svc/keycloak 8080:80 -n $NS"
echo ""
echo "  All pods:"
kubectl get pods --namespace "$NS"
