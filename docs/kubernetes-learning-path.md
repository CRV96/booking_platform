# Kubernetes Learning Path — Booking Platform

## Prerequisites

- Docker Desktop installed and running
- Enable Kubernetes: **Docker Desktop → Settings → Kubernetes → Enable Kubernetes → Apply**
- Verify the cluster is up:

```bash
kubectl get nodes
# Expected: one node named "docker-desktop" in Ready state

kubectl get pods --all-namespaces
# Expected: system pods running in kube-system namespace
```

---

## How this project maps to Kubernetes concepts

| What you have now | Kubernetes equivalent |
|---|---|
| `docker-compose.services.yaml` container | **Pod** (one running instance) |
| `restart: always` in compose | **Deployment** (manages replicas + restarts) |
| Container name used by other services | **Service** (internal DNS, e.g. `user-service:8080`) |
| `config/dev/*.properties` | **ConfigMap** |
| DB passwords, Keycloak secrets | **Secret** |
| Nginx reverse proxy | **Ingress** |
| `docker-compose.startup.yaml` infra | **Helm charts** (Kafka, Redis, Postgres, etc.) |
| Spring Cloud Config Server | **ConfigMap** (Phase 5 — later) |
| Eureka service discovery | **kube-dns** (Phase 5 — later) |

---

## Phase 1 — Learn the primitives with user-service

**Goal:** Deploy `user-service` + its Postgres database. Learn `kubectl` basics.

### 1.1 Create the namespace

All your resources will live in one namespace to keep things tidy.

```bash
kubectl create namespace booking-platform
kubectl config set-context --current --namespace=booking-platform
```

### 1.2 Create the folder structure

```
k8s/
  namespace.yaml
  phase-1-user-service/
    postgres-secret.yaml
    postgres-deployment.yaml
    postgres-service.yaml
    user-service-configmap.yaml
    user-service-deployment.yaml
    user-service-service.yaml
```

### 1.3 Postgres Secret

Secrets store sensitive data base64-encoded. Never commit real passwords.

```yaml
# k8s/phase-1-user-service/postgres-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-user-secret
  namespace: booking-platform
type: Opaque
stringData:
  POSTGRES_DB: user_service_db
  POSTGRES_USER: postgres
  POSTGRES_PASSWORD: postgres
```

Apply it:
```bash
kubectl apply -f k8s/phase-1-user-service/postgres-secret.yaml
kubectl get secrets
```

### 1.4 Postgres Deployment + Service

```yaml
# k8s/phase-1-user-service/postgres-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres-user
  namespace: booking-platform
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres-user
  template:
    metadata:
      labels:
        app: postgres-user
    spec:
      containers:
        - name: postgres
          image: postgres:16
          ports:
            - containerPort: 5432
          envFrom:
            - secretRef:
                name: postgres-user-secret
```

```yaml
# k8s/phase-1-user-service/postgres-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres-user        # other pods reach this at postgres-user:5432
  namespace: booking-platform
spec:
  selector:
    app: postgres-user
  ports:
    - port: 5432
      targetPort: 5432
```

Apply:
```bash
kubectl apply -f k8s/phase-1-user-service/postgres-deployment.yaml
kubectl apply -f k8s/phase-1-user-service/postgres-service.yaml
kubectl get pods    # watch postgres-user pod come up
kubectl logs <pod-name>
```

### 1.5 user-service ConfigMap

ConfigMaps replace environment variables and your `config/dev/user-service.properties`.

```yaml
# k8s/phase-1-user-service/user-service-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
  namespace: booking-platform
data:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-user:5432/user_service_db
  SPRING_DATASOURCE_USERNAME: postgres
  CONFIG_SERVER_URL: http://config-service:8888     # still using your config-server for now
  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-service:8761/eureka
```

### 1.6 user-service Deployment

```yaml
# k8s/phase-1-user-service/user-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: booking-platform
spec:
  replicas: 1
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: booking-platform/user-service:latest   # built locally with Dockerfile.service
          imagePullPolicy: Never                         # tells k8s to use local Docker image
          ports:
            - containerPort: 8080
            - containerPort: 9090    # gRPC port
          envFrom:
            - configMapRef:
                name: user-service-config
            - secretRef:
                name: postgres-user-secret
          readinessProbe:            # k8s won't send traffic until this passes
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:             # k8s restarts the pod if this fails
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 15
```

Build the image first (Docker Desktop k8s shares the local Docker daemon):
```bash
docker build -f infrastructure/docker/Dockerfile.service \
  --build-arg SERVICE_NAME=user-service \
  -t booking-platform/user-service:latest .
```

### 1.7 user-service Service

```yaml
# k8s/phase-1-user-service/user-service-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: booking-platform
spec:
  selector:
    app: user-service
  ports:
    - name: http
      port: 8080
      targetPort: 8080
    - name: grpc
      port: 9090
      targetPort: 9090
```

### 1.8 Essential kubectl commands to learn now

```bash
# See what's running
kubectl get pods
kubectl get services
kubectl get configmaps
kubectl get secrets

# Debug a pod
kubectl describe pod <pod-name>       # events, errors, config
kubectl logs <pod-name>               # stdout logs
kubectl logs <pod-name> -f            # follow logs (like docker logs -f)
kubectl logs <pod-name> --previous    # logs from a crashed pod

# Shell into a running pod
kubectl exec -it <pod-name> -- /bin/sh

# Delete and recreate (your main "restart" command)
kubectl rollout restart deployment/user-service

# Watch pods in real time
kubectl get pods -w
```

---

## Phase 2 — Add the GraphQL gateway + Ingress

**Goal:** Expose the platform to the outside world via a proper Ingress, replacing Nginx.

### 2.1 Install ingress-nginx

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.0/deploy/static/provider/cloud/deploy.yaml

# Wait for it to be ready
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### 2.2 graphql-gateway Deployment + Service

Same pattern as user-service. Key difference: the gateway needs to reach all downstream services.
Your gRPC clients in the gateway reference services by name — in k8s those names become the
`Service` names you defined (e.g. `user-service`, `booking-service`).

```yaml
# k8s/phase-2-gateway/graphql-gateway-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: graphql-gateway
  namespace: booking-platform
spec:
  selector:
    app: graphql-gateway
  ports:
    - port: 8080
      targetPort: 8080
```

### 2.3 Ingress resource

```yaml
# k8s/phase-2-gateway/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: booking-platform-ingress
  namespace: booking-platform
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx
  rules:
    - host: booking.local           # add this to /etc/hosts → 127.0.0.1
      http:
        paths:
          - path: /graphql
            pathType: Prefix
            backend:
              service:
                name: graphql-gateway
                port:
                  number: 8080
```

Add to `/etc/hosts`:
```
127.0.0.1  booking.local
```

Now `http://booking.local/graphql` reaches your gateway from the browser.

---

## Phase 3 — Deploy infrastructure via Helm

**Goal:** Replace `docker-compose.startup.yaml` with Helm charts. Stop hand-writing manifests for Kafka, Redis, etc.

### 3.1 Install Helm

```bash
brew install helm
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

### 3.2 Deploy each infrastructure component

**PostgreSQL** (for booking-service and payment-service):
```bash
helm install postgres bitnami/postgresql \
  --namespace booking-platform \
  --set auth.postgresPassword=postgres \
  --set auth.database=booking_db
```

**MongoDB** (for event-service, ticket-service, analytics-service):
```bash
helm install mongodb bitnami/mongodb \
  --namespace booking-platform \
  --set auth.rootPassword=admin \
  --set "auth.usernames[0]=admin" \
  --set "auth.passwords[0]=admin" \
  --set "auth.databases[0]=event_db"
```

**Redis** (for booking-service locking + gateway rate limiting):
```bash
helm install redis bitnami/redis \
  --namespace booking-platform \
  --set auth.enabled=false
```

**Kafka**:
```bash
helm install kafka bitnami/kafka \
  --namespace booking-platform \
  --set listeners.client.protocol=PLAINTEXT
```

**Keycloak**:
```bash
helm install keycloak bitnami/keycloak \
  --namespace booking-platform \
  --set auth.adminPassword=admin
```

Check everything is up:
```bash
kubectl get pods
helm list -n booking-platform
```

### 3.3 What Helm gives you

- StatefulSets with proper persistent volumes (data survives pod restarts)
- Services with the right DNS names your Spring Boot apps already expect
- Ability to upgrade config with `helm upgrade` instead of editing yaml manually

---

## Phase 4 — Deploy remaining services

**Goal:** Bring up all 10 services following the same pattern from Phase 1.

### Recommended deploy order (respects startup dependencies)

```
1. config-service       ← no dependencies
2. eureka-service       ← needs config-service
3. user-service         ← needs config, eureka, postgres, keycloak
4. event-service        ← needs config, eureka, mongodb
5. booking-service      ← needs config, eureka, postgres, redis, kafka, event-service (gRPC)
6. payment-service      ← needs config, eureka, postgres, kafka
7. ticket-service       ← needs config, eureka, mongodb, kafka
8. notification-service ← needs config, eureka, kafka, user-service (gRPC), booking-service (gRPC)
9. analytics-service    ← needs config, eureka, mongodb, kafka
10. graphql-gateway     ← needs all of the above (gRPC clients)
```

### One Makefile to build all images

Create `k8s/Makefile`:

```makefile
SERVICES = config-service eureka-service user-service event-service \
           booking-service payment-service ticket-service \
           notification-service analytics-service graphql-gateway

build-all:
	@for svc in $(SERVICES); do \
		echo "Building $$svc..."; \
		docker build -f infrastructure/docker/Dockerfile.service \
			--build-arg SERVICE_NAME=$$svc \
			-t booking-platform/$$svc:latest . ; \
	done

apply-all:
	kubectl apply -f k8s/ --recursive -n booking-platform
```

```bash
make build-all
make apply-all
```

---

## Phase 5 — Replace Spring Cloud Config + Eureka (native k8s)

**Goal:** Remove two services by using k8s-native equivalents. This is the most educational phase.

### 5.1 Replace Spring Cloud Config with ConfigMaps

Instead of `config-service` serving `config/dev/user-service.properties`,
you create a ConfigMap per service and mount it directly.

```yaml
# k8s/phase-5-native/user-service-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
  namespace: booking-platform
data:
  user-service.properties: |
    server.port=8080
    spring.datasource.url=jdbc:postgresql://postgres:5432/user_db
    grpc.server.port=9090
    # ... rest of your properties
```

Mount it into the pod as a file:
```yaml
# inside the Deployment spec
volumes:
  - name: config-volume
    configMap:
      name: user-service-config
containers:
  - name: user-service
    volumeMounts:
      - name: config-volume
        mountPath: /config
    env:
      - name: SPRING_CONFIG_LOCATION
        value: /config/user-service.properties
```

Then remove `spring.config.import` from your service — config-service is no longer needed.

### 5.2 Replace Eureka with kube-dns

In k8s, every `Service` gets a DNS name automatically:
`<service-name>.<namespace>.svc.cluster.local`

So `user-service` is reachable at `user-service.booking-platform.svc.cluster.local`
or simply `user-service` within the same namespace.

In each service's config, change:
```properties
# Before (Eureka)
eureka.client.serviceUrl.defaultZone=http://eureka-service:8761/eureka

# After (k8s — disable Eureka entirely)
eureka.client.enabled=false
spring.cloud.discovery.enabled=false

# gRPC client addresses now point to k8s Service names
grpc.client.user-service.address=static://user-service:9090
```

After this phase you can remove `config-service` and `eureka-service` deployments entirely —
the platform runs on 8 services instead of 10.

---

## Phase 6 — Health, scaling, and observability

**Goal:** Make the platform production-aware.

### 6.1 Resource limits

Add to every Deployment container spec so one service can't starve the others:

```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

### 6.2 Horizontal Pod Autoscaler

Scale booking-service automatically when CPU goes above 70%:

```yaml
# k8s/phase-6-ops/booking-service-hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: booking-service-hpa
  namespace: booking-platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: booking-service
  minReplicas: 1
  maxReplicas: 4
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

```bash
kubectl apply -f k8s/phase-6-ops/booking-service-hpa.yaml
kubectl get hpa   # watch it in action
```

### 6.3 Prometheus + Grafana in k8s

Your observability stack is already configured — deploy it with the community Helm chart:

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace booking-platform \
  --set grafana.adminPassword=admin
```

This deploys Prometheus, Grafana, and Alertmanager. Your Spring Boot Actuator `/actuator/prometheus`
endpoints are auto-discovered via `ServiceMonitor` resources.

### 6.4 Zipkin

```bash
helm install zipkin openzipkin/zipkin --namespace booking-platform
```

---

## Useful kubectl cheatsheet

```bash
# Cluster overview
kubectl get all -n booking-platform

# Deployment management
kubectl rollout status deployment/booking-service
kubectl rollout restart deployment/booking-service
kubectl rollout history deployment/booking-service
kubectl rollout undo deployment/booking-service     # rollback

# Scale manually
kubectl scale deployment/booking-service --replicas=3

# Debugging
kubectl describe pod <pod-name>
kubectl logs <pod-name> -f
kubectl exec -it <pod-name> -- /bin/sh

# Port forward a service to localhost (useful for testing)
kubectl port-forward service/graphql-gateway 8080:8080
kubectl port-forward service/grafana 3000:3000

# Helm
helm list -n booking-platform
helm upgrade <release> <chart> --namespace booking-platform
helm uninstall <release> --namespace booking-platform
```

---

## Suggested learning order

| Phase | What you learn | Time estimate |
|---|---|---|
| 1 | Pod, Deployment, Service, Secret, ConfigMap, kubectl basics | 1–2 days |
| 2 | Ingress, traffic routing, DNS | half a day |
| 3 | Helm, StatefulSets, persistent volumes | 1 day |
| 4 | Full platform migration, startup ordering | 1–2 days |
| 5 | k8s-native config + service discovery | 1 day |
| 6 | HPA, resource limits, observability | 1 day |
