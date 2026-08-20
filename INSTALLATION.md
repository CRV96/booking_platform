# Installation Guide

Follow these steps after cloning the repository.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21 | Runtime and compilation |
| Maven | 3.9+ | Build tool (or use included `./mvnw`) |
| Docker & Docker Compose | Latest | Infrastructure and optional full-stack deployment |
| OpenSSL | 1.1+ | Generating mTLS certificates for local runs (`infrastructure/certs/generate-certs.sh`) |
| Git | Latest | Source control |

## Option A: Local Development (Services on Host)

Run infrastructure in Docker and services directly on your machine via Maven. Best for active development with hot-reload and IDE debugging.

### 1. Create your `.env` file

Copy the template below into a `.env` file at the repo root and fill in any values you want to override. The file is gitignored — it will never be committed.

```bash
# Run from the repo root:
touch .env 
```

See and add to the `.env` the environment variables. You can see here the [Environment Variables](#environment-variables) section for the full list of variables, their default values, and rotation instructions.

> **Note:** `LOG_PATH` must be set before starting Docker (step 2) because
> Promtail mounts `${LOG_PATH}/logs` as a read-only volume. If you forget it,
> Docker Compose will create an empty folder and Promtail will see no log files.

### 2. Start Infrastructure

```bash
docker compose -f infrastructure/docker/docker-compose.startup.yaml up -d
```

Wait for all containers to be healthy (especially Keycloak, which imports the realm on first start). You can check with:

```bash
docker compose -f infrastructure/docker/docker-compose.startup.yaml ps
```

This starts: PostgreSQL (3 databases), MongoDB (Atlas-local — supports vector search), Redis, Kafka, Ollama (local embedding model server for semantic search), Keycloak, Zipkin, Prometheus, Grafana, Loki, Promtail, Mongo Express, RedisInsight, MailHog, Kafka UI, and SonarQube.

> **Semantic search (optional):** event-service can serve AI "smart results" alongside keyword search, powered by [Spring AI](https://spring.io/projects/spring-ai) + a local Ollama embedding model (no API key, no cost). It is **off by default**. To enable it, see [Semantic Search](#semantic-search) below.

### 3. Generate mTLS certificates

Services on the `dev` profile have **mTLS enabled by default** (`GRPC_MTLS_ENABLED=true` in `config/dev`), so their gRPC channels need certificates before they will start. Generate them once — the script creates a CA and per-service certs and copies them into each service's `src/main/resources/certs/`:

```bash
./infrastructure/certs/generate-certs.sh
```

The certificates are gitignored, so a fresh clone does **not** have them — this step is required the first time (and again when they expire; they are valid for 365 days). The script is idempotent: existing certs are skipped.

> **Just want plaintext for quick local debugging?** Skip this step and disable mTLS instead by exporting `GRPC_MTLS_ENABLED=false` in your shell (or `.env`) before starting services. Both client and server sides read the same flag, so they stay in sync. See [mTLS Certificates](#mtls-certificates) for details.
>
> Docker (Option B) and Kubernetes do **not** need this step — they set `GRPC_MTLS_ENABLED=false` already.

### 4. Start Services

Start in this order (config-service and eureka-service must be first):

```bash
# 1. Config Server (must start first — other services fetch config from here)
./run-service.sh config-service

# 2. Eureka (Service Discovery — services register here)
./run-service.sh eureka-service

# 3. All other services (any order, each in a separate terminal)
./run-service.sh user-service
./run-service.sh event-service
./run-service.sh booking-service
./run-service.sh payment-service
./run-service.sh ticket-service
./run-service.sh notification-service
./run-service.sh analytics-service
./run-service.sh graphql-gateway
```

By default, services run with the `dev` profile.

> The `run-service.sh` script uses the environment variables already exported in your shell. Source your `.env` file first (see step 1) so all secrets are available before starting services.

#### Debugging

```bash
./run-service.sh user-service --debug       # Remote debug on port 5008
./run-service.sh user-service -d -p 5099    # Custom debug port
./run-service.sh user-service -d -s         # Suspend until debugger attaches
```

Each service has a unique default debug port (5005–5014). Run `./run-service.sh --help` for details.

---

## Option B: Full Docker Deployment

Run **everything** in Docker — infrastructure, nginx, and all microservices. Best for testing the full stack without installing Java.

```bash
docker compose -f infrastructure/docker/docker-compose.yaml up --build -d
```

This builds all services from source using the multi-stage `Dockerfile.service` and starts them alongside the infrastructure. The first build takes several minutes (Maven downloads dependencies); subsequent builds use Docker layer caching.

### API Entry Point (Docker)

In Docker deployment, **nginx** acts as the reverse proxy in front of the graphql-gateway. All API traffic goes through port **80**:

| URL | Purpose |
|-----|---------|
| `http://localhost/graphql` | GraphQL API endpoint |
| `http://localhost/graphiql` | GraphiQL interactive UI |

> The `graphql-gateway` container does **not** expose port 8080 to the host in Docker mode — nginx is the only public entry point. This is intentional: clients always go through the proxy, which stamps the real client IP for rate limiting.

To rebuild a single service after code changes:

```bash
docker compose -f infrastructure/docker/docker-compose.yaml build --no-cache <service-name>
docker compose -f infrastructure/docker/docker-compose.yaml up -d <service-name>
```

To stop everything:

```bash
docker compose -f infrastructure/docker/docker-compose.yaml down
```

---

## Option C: Kubernetes (Local)

Run the full platform on a local Kubernetes cluster. This mirrors a production-style deployment and is useful for learning Kubernetes or testing the platform with orchestration.

### Prerequisites

| Tool | Purpose |
|------|---------|
| `kubectl` | Kubernetes CLI |
| `helm` | Package manager for k8s infra (Postgres, MongoDB, Redis) |
| A running cluster | kind (`kind create cluster --name desktop`) or Docker Desktop with Kubernetes enabled |

### 1. Fill in your secrets

The file `infrastructure/k8s/.env.k8s` is gitignored and holds all sensitive values. Edit it before running:

```bash
# infrastructure/k8s/.env.k8s
DB_POSTGRES_PASSWORD=admin
DB_MONGO_USERNAME=admin
DB_MONGO_PASSWORD=admin
USER_SERVICE_KEYCLOAK_CLIENT_SECRET=user-service-secret
NOTIFICATION_SERVICE_KEYCLOAK_CLIENT_SECRET=notification-service-secret
STRIPE_SECRET_KEY=sk_test_replace_with_your_key
```

### 2. Run the script

```bash
./infrastructure/k8s/run.sh
```

The script handles everything in order:

1. Builds all 10 service Docker images
2. Loads them into the cluster (automatically detected if kind)
3. Creates namespace, ConfigMaps, and Secrets
4. Installs ingress-nginx controller
5. Installs infrastructure via Helm (PostgreSQL, MongoDB, Redis)
6. Deploys Kafka and Keycloak directly (Bitnami images unavailable on Docker Hub)
7. Deploys services in dependency order with readiness checks

On subsequent runs, healthy Helm releases are skipped and only changed manifests are re-applied.

### 3. k8s folder structure

```
infrastructure/k8s/
├── run.sh                     ← single entrypoint — run this
├── .env.k8s                   ← secrets (gitignored, fill before first run)
├── namespace.yaml             ← booking-platform namespace
├── common/
│   └── configmap.yaml         ← env vars shared by all services
├── helm/
│   ├── postgres/values.yaml   ← creates userdb, bookingdb, paymentdb
│   ├── mongodb/values.yaml    ← root user, all 3 mongo databases
│   ├── redis/values.yaml      ← no auth, single node
│   ├── kafka/values.yaml      ← KRaft mode (no Zookeeper), 1 replica
│   └── keycloak/values.yaml   ← HTTP only, uses existing postgres
├── infrastructure/
│   ├── kafka/                 ← direct deployment (apache/kafka:3.9.0)
│   ├── keycloak/              ← direct deployment (quay.io/keycloak/keycloak:26.0)
│   ├── zipkin/                ← direct deployment
│   └── mailhog/               ← direct deployment
├── services/
│   └── <service-name>/
│       ├── configmap.yaml     ← non-sensitive config (URLs, addresses)
│       ├── deployment.yaml    ← pod spec, probes, resource limits
│       └── service.yaml       ← internal DNS name
└── ingress/
    └── ingress.yaml           ← routes booking.local → graphql-gateway
```

### 4. Accessing services

The graphql-gateway is the only service exposed through the Ingress. Everything else requires port-forwarding:

```bash
# GraphQL gateway (needed for frontend dev)
kubectl port-forward svc/graphql-gateway 8080:8080 -n booking-platform

# Eureka dashboard
kubectl port-forward svc/eureka-service 8761:8761 -n booking-platform
# → http://localhost:8761

# Keycloak admin console
kubectl port-forward svc/keycloak 8080:80 -n booking-platform
# → http://localhost:8080  (admin / admin)

# Zipkin tracing
kubectl port-forward svc/zipkin 9411:9411 -n booking-platform
# → http://localhost:9411

# Mailhog email UI
kubectl port-forward svc/mailhog 8025:8025 -n booking-platform
# → http://localhost:8025
```

### 5. Frontend with Kubernetes

The Angular app's `proxy.conf.json` already forwards `/graphql` to `http://localhost:8080`. Port-forward the gateway first, then start the frontend normally:

```bash
# Terminal 1
kubectl port-forward svc/graphql-gateway 8080:8080 -n booking-platform

# Terminal 2
cd frontend && npm start
# → http://localhost:4200
```

### 6. Keycloak realm import

Keycloak starts empty in Kubernetes — the realm JSON is not auto-imported (unlike Docker). Import it manually after Keycloak is running:

```bash
kubectl port-forward svc/keycloak 8080:80 -n booking-platform
```

Then open `http://localhost:8080`, log in with `admin / admin`, go to **Create realm**, and import `init/keycloak/booking-platform-realm.json`.

### 7. Useful commands

```bash
# Watch all pods
kubectl get pods -n booking-platform -w

# Logs for a service
kubectl logs -n booking-platform -l app=user-service -f

# Restart a service after a code change
docker build -f infrastructure/docker/Dockerfile.service \
  --build-arg SERVICE_NAME=user-service \
  -t booking-platform/user-service:latest .
kind load docker-image booking-platform/user-service:latest --name <cluster-name>
kubectl rollout restart deployment/user-service -n booking-platform

# Describe a pod (events, errors)
kubectl describe pod -n booking-platform -l app=booking-service
```

---

## Config Server

The config server uses the native filesystem to serve configuration to all services.

### How It Works

`services/config-service/src/main/resources/application.properties` contains:

```properties
spring.cloud.config.server.native.search-locations=${CONFIG_SERVER_CONFIGURATIONS_PATH}/{profile}
```

The `CONFIG_SERVER_CONFIGURATIONS_PATH` environment variable must point to the `config/` folder. Services request their config based on their active profile:

- `spring.profiles.active=dev` → config server serves from `config/dev/<service-name>.properties`
- `spring.profiles.active=prod` → config server serves from `config/prod/<service-name>.properties`

### Config Folder Structure

```
config/
├── dev/                            # Development properties
│   ├── user-service.properties
│   ├── event-service.properties
│   ├── booking-service.properties
│   ├── payment-service.properties
│   ├── ticket-service.properties
│   ├── notification-service.properties
│   ├── analytics-service.properties
│   ├── graphql-gateway.properties
│   └── eureka-service.properties
└── prod/                           # Production properties
    └── ...
```

### Docker vs Local

- **Local (Option A):** Set `CONFIG_SERVER_CONFIGURATIONS_PATH` to the absolute path on your host (e.g., `/Users/yourname/booking-platform/config`)
- **Docker (Option B):** The `docker-compose.services.yaml` mounts the `config/` folder into the container at `/config` and sets `CONFIG_SERVER_CONFIGURATIONS_PATH=/config` automatically

---

## Keycloak

Keycloak provides OAuth2/OpenID Connect authentication. The realm (`booking-platform`) is auto-imported on first start from `init/keycloak/booking-platform-realm.json`.

### Admin Console

| Setting | Value |
|---------|-------|
| **URL** | http://localhost:8180 |
| **Username** | admin |
| **Password** | admin |

### Client Secret

The `user-service` communicates with Keycloak's Admin API using the `user-service-admin` client.

**Default secret:** `user-service-secret` (configured in the realm JSON)

If you need to regenerate:
1. Open Keycloak Admin Console: http://localhost:8180
2. Login with `admin` / `admin`
3. Select realm: **booking-platform**
4. Go to: **Clients** → **user-service-admin** → **Credentials** tab
5. Click **Regenerate** and update `USER_SERVICE_KEYCLOAK_CLIENT_SECRET`

### Test Users

| Username | Password | Role | Group |
|----------|----------|------|-------|
| admin | admin123 | employee | employees |
| john.doe | customer123 | customer | customers |
| jane.smith | customer123 | customer | customers |
| carlos.garcia | customer123 | customer | customers |

### Get an OAuth2 Token

```bash
curl -s -X POST http://localhost:8180/realms/booking-platform/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=booking-app&username=john.doe&password=customer123"
```

---

## nginx (Reverse Proxy)

nginx sits in front of the `graphql-gateway` container in Docker deployment. It is responsible for:

- **Single entry point** — all traffic enters on port 80; clients never talk directly to the gateway container
- **IP stamping** — nginx overwrites `X-Forwarded-For` with the real client IP (`$remote_addr`) before forwarding, preventing clients from spoofing their IP to bypass rate limiting

### Configuration

The nginx config lives at `infrastructure/nginx/nginx.conf`. It defines one upstream (`graphql-gateway:8080`) and one `server` block on port 80 that proxies everything through.

```
Client → nginx:80 → graphql-gateway:8080 (internal Docker network)
```

### nginx is Docker-only

nginx is only used in **Option B (Full Docker)**. In **Option A (local dev)**, the graphql-gateway runs directly on `localhost:8080` and you access it directly — no nginx involved.

| Mode | GraphQL API URL |
|------|----------------|
| Option A (local dev) | `http://localhost:8080/graphql` |
| Option B (Docker) | `http://localhost/graphql` |

---

## mTLS Certificates

mTLS (mutual TLS) secures gRPC communication between services. Both client and server authenticate each other using certificates.

- **Local development (Option A)**: **enabled by default** (`GRPC_MTLS_ENABLED=true` in `config/dev`). You must generate certificates before starting services (see [Option A → step 3](#3-generate-mtls-certificates)), or disable mTLS for plaintext debugging.
- **Docker (Option B) / Kubernetes**: **disabled by default** (`docker-compose.services.yaml` sets `GRPC_MTLS_ENABLED=false`), so no certificates are required.
- **Production**: recommended for service-to-service security.

### Generate Certificates

```bash
cd infrastructure/certs
./generate-certs.sh
```

This generates:

| File | Purpose |
|------|---------|
| `ca.crt` / `ca.key` | Root Certificate Authority (signs all certs) |
| `user-service.crt` / `user-service.key` | gRPC server certificate |
| `graphql-gateway.crt` / `graphql-gateway.key` | gRPC client certificate |

The script automatically copies certificates to each service's `src/main/resources/certs/` directory.

### Enable/Disable mTLS

```bash
# Disable mTLS
export GRPC_MTLS_ENABLED=false

# Enable mTLS (default)
export GRPC_MTLS_ENABLED=true
```

Or set in config properties:

```properties
# Server-side (user-service, event-service, etc.)
grpc.server.security.enabled=false

# Client-side (graphql-gateway)
grpc.client.security.enabled=false
```

**Note**: Both sides must have matching settings — either both enabled or both disabled.

### Troubleshooting mTLS

| Issue | Solution |
|-------|----------|
| `UNAVAILABLE: io exception` | Certificates not copied to service resources |
| `CERTIFICATE_VERIFY_FAILED` | CA mismatch — regenerate all certs together |
| `handshake failed` | Check both services have mTLS enabled |

---

## PostgreSQL

By default, PostgreSQL uses `admin` / `admin` as credentials. These are configured in two places:

1. **Docker Compose** (`infrastructure/docker/docker-compose.startup.yaml`) — defines the database credentials when the container starts
2. **Service properties** (`config/dev/*.properties`) — services use `${DB_POSTGRES_USERNAME}` and `${DB_POSTGRES_PASSWORD}` environment variables

Three databases are created automatically:
- `userdb` — user-service
- `bookingdb` — booking-service
- `paymentdb` — payment-service

Schema migrations run automatically via Flyway on service startup.

---

## Semantic Search

event-service can return AI **"smart results"** — events matched by *meaning* — alongside the classic keyword search, exposed via the `events(... aiSearch: true)` GraphQL query and the frontend's **✨ AI Search** toggle. It uses [Spring AI](https://spring.io/projects/spring-ai) with:

- **Ollama** (`bkg-ollama` container) running `nomic-embed-text` — a local embedding model, **no API key, no cost**.
- **MongoDB Atlas-local** `$vectorSearch` — vectors are stored in the `event_vectors` collection in the same MongoDB.

It is **off by default** (`SEMANTIC_SEARCH_ENABLED=false`). To enable it locally:

```bash
# 1. Pull the embedding model into the Ollama container (once, ~275 MB)
docker exec bkg-ollama ollama pull nomic-embed-text

# 2. Enable the feature for event-service (in .env or your shell)
export SEMANTIC_SEARCH_ENABLED=true

# 3. (Re)start event-service — on boot it creates the vector index and backfills
#    existing events into event_vectors via Ollama.
./run-service.sh event-service
```

> **Only one Ollama may own port 11434.** If you also run the native Ollama.app, quit it (`killall Ollama`) so requests reach the Docker container that has the model — otherwise you'll see `model "nomic-embed-text" not found`.

**Relevant settings** (`config/dev/event-service.properties`):

| Property | Default | Purpose |
| --- | --- | --- |
| `app.semantic-search.enabled` | `${SEMANTIC_SEARCH_ENABLED:false}` | Master switch. Off → event-service is a pure producer; search returns keyword results only. |
| `app.semantic-search.similarity-threshold` | `0.78` | Min similarity (Atlas cosine score, `(1+cos)/2`) for a smart result. Raise to reduce noise, lower to surface more. |
| `app.semantic-search.smart-results-limit` | `10` | Max smart results returned. |
| `spring.ai.ollama.base-url` | `${OLLAMA_BASE_URL:http://localhost:11434}` | Ollama endpoint. |
| `spring.ai.ollama.embedding.options.model` | `nomic-embed-text` | Embedding model (768 dims — must match the index). |

If Ollama is unavailable, semantic search **degrades gracefully**: search still returns classic keyword results (empty smart results), and a `event_semantic_search_fallback_total` metric increments (see [Observability](#observability-stack)).

---

## Observability Stack

The infrastructure starts the observability tools automatically:

| Tool | URL | Credentials | Purpose |
|------|-----|-------------|---------|
| Prometheus | http://localhost:9090 | none | Scrapes metrics from all services every 15s |
| Grafana | http://localhost:3000 | admin / admin | Dashboards for logs, metrics and traces |
| Loki | http://localhost:3100 | none | Log storage (queried by Grafana) |
| Zipkin | http://localhost:9411 | none | Distributed tracing across services |

### Grafana — First Steps

1. Open **http://localhost:3000** and login with `admin` / `admin`
2. Go to **Dashboards** → **Booking Platform** — all panels load automatically
3. Go to **Explore** → select **Loki** → query `{job="microservices"}` to see live service logs

### How Logs Flow to Grafana

```
Java service (host machine)
  → logback-spring.xml (JSON format via LogstashEncoder)
  → ${LOG_PATH}/logs/<service-name>.log
  → Promtail (bind-mounted volume: ${LOG_PATH}/logs → /logs)
  → Loki
  → Grafana → Explore → Loki
```

> **This requires `LOG_PATH` to be set** so Promtail can find the log files.
> If `LOG_PATH` is not set, logs will still appear in the console but
> will not be visible in Grafana.

### Useful LogQL Queries (Grafana → Explore → Loki)

```logql
# All logs from a specific service
{service="notification-service"}

# Only errors across all services
{job="microservices"} | level="ERROR"

# Search inside log messages
{service="event-service"} |= "DLT"

# Follow a trace across services
{job="microservices"} |= "your-trace-id-here"
```

### Prometheus Targets

Check which services are being scraped: **http://localhost:9090/targets**

All services expose metrics at `/actuator/prometheus`. Only three actuator endpoints
are accessible without authentication:

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Service health (UP/DOWN) |
| `/actuator/info` | App version and build metadata |
| `/actuator/prometheus` | All Micrometer metrics in Prometheus format |

---

## Dev Tools

| Tool | URL | Purpose |
|------|-----|---------|
| Mongo Express | http://localhost:8090 | Browse MongoDB collections (event-service, analytics-service) |
| RedisInsight | http://localhost:5540 | Browse Redis keys (booking locks, idempotency, rate limiting) |
| MailHog | http://localhost:8025 | Catch emails sent by notification-service |
| Kafka UI | http://localhost:8085 | Browse Kafka topics, consumer groups, messages |

### RedisInsight Setup

1. Open **http://localhost:5540**
2. Click **Add Redis database**
3. Enter: Host `bkg-redis`, Port `6379`, leave password empty
4. Click **Add Redis Database**

> Use `bkg-redis` as the host (not `localhost`) because RedisInsight runs inside
> the same Docker network. From your host machine, use `localhost:6379`.

---

## Frontend

The Angular SPA lives in `frontend/`. It connects to the GraphQL gateway via a dev proxy.

### Prerequisites

| Tool | Version |
|------|---------|
| Node.js | 18+ |
| npm | 9+ |

### Start the dev server

```bash
cd frontend
npm install          # first time only
npm start            # http://localhost:4200
```

`npm start` runs `ng serve --proxy-config proxy.conf.json`, which forwards all `/graphql` requests to `http://localhost:8080`. The backend (Option A or Option B) must be running first.

| Mode | Frontend URL | GraphQL URL |
|------|-------------|-------------|
| Option A (local dev) | http://localhost:4200 | http://localhost:8080/graphql |
| Option B (Docker) | http://localhost:4200 | http://localhost/graphql |
| Option C (Kubernetes) | http://localhost:4200 | http://localhost:8080/graphql (port-forwarded) |

> For Option B, update `proxy.conf.json` to point to `http://localhost` instead of `http://localhost:8080`.
> For Option C, run `kubectl port-forward svc/graphql-gateway 8080:8080 -n booking-platform` before starting the frontend.

### Build for production

```bash
cd frontend
npm run build        # output in frontend/dist/
```

See **[docs/frontend-guide.md](docs/frontend-guide.md)** for a complete walkthrough of the Angular app structure, components, and how to make changes.

---

## SonarQube (Optional — Local)

For local code quality analysis:

1. Open SonarQube: http://localhost:9000
2. Login with `admin` / `admin` (change password on first login)
3. Generate a token: **My Account** → **Security** → **Generate Tokens**
4. Run analysis:

```bash
./infrastructure/sonarqube/run-sonar.sh <your-token>
```

---

## CI Pipeline

The project includes a GitHub Actions CI pipeline (`.github/workflows/ci.yml`) that runs automatically on pushes to `main` and pull requests.

```
Build → Test → Docker Build → SonarQube Analysis
```

See [P6-04 documentation](docs/P6-04-github-actions-ci-pipeline.md) for full details.

### SonarCloud Setup (for CI)

1. Sign in at [sonarcloud.io](https://sonarcloud.io) with GitHub
2. Import the repository
3. **Disable Automatic Analysis**: Administration → Analysis Method
4. Add GitHub secrets:
   - `SONAR_TOKEN` — SonarCloud: My Account → Security → Generate Token
   - `SONAR_ORGANIZATION` — Your SonarCloud organization key (e.g., `crv96`)
   - `SONAR_PROJECT_KEY` — Project key from SonarCloud: your project → Information (e.g., `CRV96_booking-platform`)

The pipeline runs Build, Test, and Docker jobs even without SonarCloud. Only the SonarQube job is skipped.

---

## Postman Collections

Two Postman collections are provided in the `postman/` folder for quick API testing:

| Collection | Base URL | Purpose |
|------------|----------|---------|
| `Booking-Platform-GraphQL.postman_collection.json` | `http://localhost:8080` | Local dev (Option A) — direct gateway access |
| `Booking-Platform-Docker.postman_collection.json` | `http://localhost` | Docker deployment (Option B) — through nginx |

Import into Postman: **File** → **Import** → select the JSON file.

---

## Environment Variables

Create a `.env` file at the repo root with the variables below. This file is gitignored and must never be committed. The values listed here are the **local development defaults** — they are intentionally simple for a local-only environment and should be rotated to strong, unique values for any shared or production deployment.

```bash
# ── PostgreSQL ────────────────────────────────────────────────────────────────
# Credentials for the shared PostgreSQL instance (userdb, bookingdb, paymentdb).
# Default matches the value hardcoded in docker-compose.startup.yaml for local dev.
# Rotate: change here + update POSTGRES_USER/POSTGRES_PASSWORD in docker-compose.startup.yaml
#         + recreate the postgres volume (docker volume rm bkg-postgres-data).
DB_POSTGRES_USERNAME=admin
DB_POSTGRES_PASSWORD=admin

# ── MongoDB ───────────────────────────────────────────────────────────────────
# Credentials for the shared MongoDB instance (eventdb, analyticsdb, ticketdb).
# Rotate: change here + update MONGO_INITDB_ROOT_USERNAME/PASSWORD in docker-compose.startup.yaml
#         + recreate the mongodb volume (docker volume rm bkg-mongodb-data).
DB_MONGO_USERNAME=admin
DB_MONGO_PASSWORD=admin

# ── Keycloak service-account secrets ─────────────────────────────────────────
# Client secrets for service-to-service gRPC calls authenticated via Keycloak.
# Default values match what is configured in init/keycloak/booking-platform-realm.json.
# Rotate: generate a new secret in Keycloak Admin (Clients → <client> → Credentials → Regenerate),
#         update the value here, and restart the affected service.
USER_SERVICE_KEYCLOAK_CLIENT_SECRET=user-service-secret
NOTIFICATION_SERVICE_KEYCLOAK_CLIENT_SECRET=notification-service-secret

# ── Stripe ────────────────────────────────────────────────────────────────────
# Stripe secret key used by payment-service to create and confirm payment intents.
# Get your test key from: https://dashboard.stripe.com/test/apikeys
# Rotate: generate a new key in the Stripe Dashboard and replace the value here.
# Never use a live key (sk_live_...) for local development.
STRIPE_SECRET_KEY=sk_test_replace_me

# ── Paths (local development only) ───────────────────────────────────────────
# Absolute path to the repo root — used by Promtail to mount service log files.
# Must be set before starting Docker. If omitted, Promtail will see no log files
# and logs will not appear in Grafana/Loki (console output still works).
# LOG_PATH=/Users/yourname/Developer/booking-platform

# Absolute path to the config/ folder — used by config-service when running on host.
# Not needed for full Docker deployments (the folder is mounted automatically).
# CONFIG_SERVER_CONFIGURATIONS_PATH=/Users/yourname/Developer/booking-platform/config

# ── Observability (optional) ──────────────────────────────────────────────────
# SonarQube token — only needed when running sonar analysis locally.
# Generate at: http://localhost:9000 → My Account → Security → Generate Token
# SONAR_TOKEN=

# ── gRPC mTLS (local development) ─────────────────────────────────────────────
# Toggles mutual TLS on inter-service gRPC channels. config/dev defaults this to
# `true`, so local (Option A) runs require certificates first — see "Generate
# mTLS certificates" (Option A → step 3). Set to `false` to run gRPC in plaintext
# and skip certificate generation. Docker/K8s already set this to false.
# GRPC_MTLS_ENABLED=true
```

### How Docker Compose resolves these variables

Docker Compose automatically reads `.env` from the directory where you run the command **or from the project root**. Since the wrapper `docker-compose.yaml` is under `infrastructure/docker/` but uses `../../` relative paths, it resolves `.env` from the repo root — which is where this file lives.

### How local Spring Boot services resolve these variables

The `.env` file is **not** loaded automatically by Java or the shell. For local runs via `run-service.sh`, the script picks up whatever is already exported in your shell. Source the file first:

```bash
# Load .env into your current shell session
export $(grep -v '^#' .env | grep -v '^$' | xargs)

# Then start services as normal
./run-service.sh user-service
```

Or add the export line to your shell profile (`~/.zshrc` / `~/.bashrc`) so it runs automatically.

### Rotating secrets

| Secret | Where to change | Additional steps |
|--------|----------------|-----------------|
| `DB_POSTGRES_PASSWORD` | `.env` + `docker-compose.startup.yaml` `POSTGRES_PASSWORD` | Recreate `bkg-postgres-data` volume |
| `DB_MONGO_PASSWORD` | `.env` + `docker-compose.startup.yaml` `MONGO_INITDB_ROOT_PASSWORD` | Recreate `bkg-mongodb-data` volume |
| `USER_SERVICE_KEYCLOAK_CLIENT_SECRET` | `.env` | Keycloak Admin → Clients → user-service → Credentials → Regenerate |
| `NOTIFICATION_SERVICE_KEYCLOAK_CLIENT_SECRET` | `.env` | Keycloak Admin → Clients → notification-service → Credentials → Regenerate |
| `STRIPE_SECRET_KEY` | `.env` | Stripe Dashboard → Developers → API keys → Roll key |
