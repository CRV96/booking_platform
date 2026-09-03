# Kubernetes Deployment

A production-shaped deployment for a local cluster (kind or Docker Desktop's Kubernetes). One script — `infrastructure/k8s/run.sh` — takes a fresh cluster to a fully running platform, and is idempotent on re-runs.

## Prerequisites

- A running Kubernetes cluster (kind, or Docker Desktop with Kubernetes enabled)
- `kubectl` and `helm` on the PATH
- Secrets filled into `infrastructure/k8s/.env.k8s` (gitignored)

## Run it

```bash
# Edit infrastructure/k8s/.env.k8s with your values first
./infrastructure/k8s/run.sh
```

On a **fresh** cluster it installs everything from scratch; on **subsequent** runs it skips already-healthy Helm releases and only re-applies changed manifests.

## What `run.sh` does, in order

The script's phases map directly to the startup dependency order:

1. **Prerequisites** — checks `kubectl`/`helm` and cluster reachability; detects a kind cluster.
2. **Environment + namespace** — loads `.env.k8s`, creates the `booking-platform` namespace.
3. **Build & load images** — builds each service image (shared Dockerfile) and, for kind, loads them into the cluster.
4. **Config, Secrets, common ConfigMap** — applies the Spring Cloud Config files, per-service Secrets, and the shared env ConfigMap.
5. **Infrastructure via Helm** — ingress-nginx, PostgreSQL, MongoDB, Redis, then Kafka, Keycloak, and Zipkin + MailHog.
6. **Services in dependency order** — config-service → eureka-service → (user, event) → (booking, payment, analytics, ticket) → notification → graphql-gateway.
7. **Ingress** — routing rules for external access.

## Folder layout

```
infrastructure/k8s/
├── run.sh                 # the single entrypoint
├── .env.k8s               # secrets (gitignored)
├── apply-secrets.sh       # helper: env → k8s Secrets
├── namespace.yaml
├── common/configmap.yaml  # shared env vars for all services
├── helm/                  # values.yaml per stateful dependency + install.sh
│   ├── postgres/ mongodb/ redis/ kafka/ keycloak/
├── infrastructure/        # direct manifests: kafka, keycloak, zipkin, mailhog
├── services/<service>/    # configmap + deployment + service (+ secret)
└── ingress/ingress.yaml
```

- **`helm/`** installs the stateful backing services from upstream charts with pinned `values.yaml`.
- **`infrastructure/`** holds direct manifests for things not installed via Helm (Kafka, Keycloak, Zipkin, MailHog).
- **`services/<service>/`** is a consistent set per service: a ConfigMap (non-secret env), a Deployment, a Service, and a Secret where credentials are needed.
- The **common ConfigMap** carries the env vars shared by every service (config-server URL, Eureka URL, Kafka brokers, `GRPC_MTLS_ENABLED=false`, etc.), resolving the `${ENV_VAR:...}` placeholders from [config](configuration).

## Accessing the platform

```bash
# GraphQL gateway (needed for the frontend dev server)
kubectl port-forward svc/graphql-gateway 8080:8080 -n booking-platform

cd frontend && npm start   # proxies /graphql → localhost:8080
```

Other UIs (Eureka, Keycloak, Zipkin, MailHog) are exposed via the ingress or port-forwarding — see [Installation → Kubernetes](INSTALLATION) for the full command list and troubleshooting.

## Related

- [Infrastructure overview](infrastructure-overview) · [Docker deployment](docker-deployment) · [Configuration](configuration) · [Installation](INSTALLATION)
