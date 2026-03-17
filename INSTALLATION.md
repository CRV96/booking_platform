# Installation Guide

Follow these steps after cloning the repository.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21 | Runtime and compilation |
| Maven | 3.9+ | Build tool (or use included `./mvnw`) |
| Docker & Docker Compose | Latest | Infrastructure and optional full-stack deployment |
| Git | Latest | Source control |

## Option A: Local Development (Services on Host)

Run infrastructure in Docker and services directly on your machine via Maven. Best for active development with hot-reload and IDE debugging.

### 1. Set Environment Variables

Export the required environment variables **before** starting Docker or services:

```bash
# Absolute path to the root of this repository (no trailing slash)
# Used by: service log file paths + Promtail volume mount in docker-compose
export LOG_PATH=/absolute/path/to/booking-platform
# Example (macOS):
# export LOG_PATH=/Users/yourname/Developer/booking-platform

# Config server — absolute path to the config/ folder
export CONFIG_SERVER_CONFIGURATIONS_PATH=/absolute/path/to/booking-platform/config
# Example (macOS):
# export CONFIG_SERVER_CONFIGURATIONS_PATH=/Users/yourname/Developer/booking-platform/config

# PostgreSQL credentials (default: admin/admin)
export DB_POSTGRES_USERNAME=admin
export DB_POSTGRES_PASSWORD=admin

# Keycloak client secret for user-service (default: user-service-secret)
export USER_SERVICE_KEYCLOAK_CLIENT_SECRET=user-service-secret
```

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

This starts: PostgreSQL (3 databases), MongoDB, Redis, Kafka, Keycloak, Zipkin, Prometheus, Grafana, Loki, Promtail, Mongo Express, RedisInsight, MailHog, Kafka UI, and SonarQube.

### 3. Start Services

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

> **1Password (optional):** The `run-service.sh` script runs services directly using your shell's exported environment variables. If you use [1Password CLI](https://developer.1password.com/docs/cli/) for secret management, open `run-service.sh` and swap the commented lines at the bottom to enable `op run` mode instead.

#### Debugging

```bash
./run-service.sh user-service --debug       # Remote debug on port 5008
./run-service.sh user-service -d -p 5099    # Custom debug port
./run-service.sh user-service -d -s         # Suspend until debugger attaches
```

Each service has a unique default debug port (5005–5014). Run `./run-service.sh --help` for details.

---

## Option B: Full Docker Deployment

Run **everything** in Docker — infrastructure and all 10 microservices. Best for testing the full stack without installing Java.

```bash
docker compose -f infrastructure/docker/docker-compose.yaml up --build -d
```

This builds all services from source using the multi-stage `Dockerfile.service` and starts them alongside the infrastructure. The first build takes several minutes (Maven downloads dependencies); subsequent builds use Docker layer caching.

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

Keycloak provides OAuth2/OpenID Connect authentication. The realm (`booking-platform`) is auto-imported on first start from `infrastructure/docker/keycloak/booking-platform-realm.json`.

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

## mTLS Certificates (Optional)

mTLS (mutual TLS) secures gRPC communication between services. Both client and server authenticate each other using certificates.

- **Development**: Optional — disabled by default for easier debugging
- **Production**: Recommended for service-to-service security

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

| Collection | Purpose |
|------------|---------|
| `Booking-Platform-GraphQL.postman_collection.json` | GraphQL queries and mutations via the gateway |
| `Booking-Platform-Docker.postman_collection.json` | Same requests configured for the Docker deployment |

Import into Postman: **File** → **Import** → select the JSON file.

---

## Production Environment Variables

For production, these additional variables are needed:

```bash
export SPRING_PROFILES_ACTIVE=prod
export POSTGRES_HOST=your-postgres-host
export DB_POSTGRES_USERNAME=your-user
export DB_POSTGRES_PASSWORD=your-password
export KEYCLOAK_URL=https://your-keycloak-url
export EUREKA_URL=http://your-eureka-url/eureka/
export ZIPKIN_URL=http://your-zipkin-url
```
