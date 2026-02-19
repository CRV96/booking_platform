# System Initialization

Follow these steps after cloning the repository.

## 1. Start Infrastructure

```bash
docker-compose -f infrastructure/docker/docker-compose.startup.yaml up -d
```

Wait for all services to be healthy (especially Keycloak, which imports the realm on first start).

> **Note:** The `LOG_PATH` environment variable must be exported **before** running this command
> (see step 4). Promtail mounts `${LOG_PATH}/logs` to read service log files.

## 2. Configure Config Server

Update `services/config-service/src/main/resources/application.properties`:

```properties
spring.cloud.config.server.native.search-locations=${CONFIG_SERVER_CONFIGURATIONS_PATH}/{profile}
```

Replace `${CONFIG_SERVER_CONFIGURATIONS_PATH}` with the absolute path to the `config` folder.

Example:
```properties
spring.cloud.config.server.native.search-locations=file:/Users/yourname/booking-platform/config/{profile}
```

### Config Folder Structure

```
config/
├── dev/                    # Development properties
│   ├── user-service.properties
│   ├── event-service.properties
│   └── ...
└── prod/                   # Production properties
    ├── user-service.properties
    ├── event-service.properties
    └── ...
```

Services fetch their config based on their active profile:
- `spring.profiles.active=dev` → fetches from `config/dev/`
- `spring.profiles.active=prod` → fetches from `config/prod/`

## 3. Keycloak Client Secret

The `user-service` needs a client secret to communicate with Keycloak's Admin API.

**Default secret:** `user-service-secret` (configured in `booking-platform-realm.json`)

If you need to regenerate the secret:
1. Open Keycloak Admin Console: http://localhost:8180
2. Login with `admin` / `admin`
3. Select realm: `booking-platform`
4. Go to: **Clients** → **user-service-admin** → **Credentials** tab
5. Click **Regenerate** and copy the new secret

## 4. Set Environment Variables

Export the required environment variables before running services:

```bash
# Required for user-service
export USER_SERVICE_KEYCLOAK_CLIENT_SECRET=<paste-secret-here>

# PostgreSQL credentials (default: admin/admin)
export DB_POSTGRES_USERNAME=admin
export DB_POSTGRES_PASSWORD=admin

# Absolute path to the root of this repository (no trailing slash)
# Used by: service log file paths + Promtail volume mount in docker-compose
export LOG_PATH=/absolute/path/to/booking-platform
# Example (macOS):
# export LOG_PATH=/Users/yourname/Developer/booking-platform
```

Or add them to your `.env` file if using the `run-service.sh` script.

> **Note:** `LOG_PATH` must be set before starting Docker (step 1) because
> Promtail mounts `${LOG_PATH}/logs` as a read-only volume. If you forget it,
> Docker Compose will create an empty folder and Promtail will see no log files.

## 5. Generate mTLS Certificates (Optional)

mTLS (mutual TLS) secures gRPC communication between services. Both client and server authenticate each other using certificates.

### When to Use mTLS

- **Development**: Optional (disabled by default for easier debugging)
- **Production**: Recommended for service-to-service security

### Generate Certificates

```bash
cd infrastructure/certs
./generate-certs.sh
```

This script generates:

| File | Purpose |
|------|---------|
| `ca.crt` / `ca.key` | Root Certificate Authority (signs all certs) |
| `user-service.crt` / `user-service.key` | gRPC server certificate |
| `graphql-gateway.crt` / `graphql-gateway.key` | gRPC client certificate |

The script automatically copies certificates to each service's `src/main/resources/certs/` directory.

### Enable/Disable mTLS

mTLS is **enabled by default**. To disable it (e.g., for debugging):

```bash
# Disable mTLS for both services
export GRPC_MTLS_ENABLED=false
```

Or set in config properties:

```properties
# user-service (server)
grpc.server.security.enabled=false

# graphql-gateway (client)
grpc.client.security.enabled=false
```

**Note**: Both services must have matching mTLS settings - either both enabled or both disabled.

### Regenerate Certificates

To regenerate all certificates:

```bash
cd infrastructure/certs
rm -f *.crt *.key *.csr *.pem *.srl
./generate-certs.sh
```

**Note**: After regenerating, restart all services that use mTLS.

### Certificate Validity

Certificates are valid for **365 days** by default. Set a reminder to regenerate before expiry.

### Troubleshooting mTLS

| Issue | Solution |
|-------|----------|
| `UNAVAILABLE: io exception` | Certificates not copied to service resources |
| `CERTIFICATE_VERIFY_FAILED` | CA mismatch - regenerate all certs together |
| `handshake failed` | Check both services have mTLS enabled |
| Service works without mTLS | Set `grpc.*.security.enabled=true` |

### PostgreSQL Credentials

By default, PostgreSQL uses `admin` / `admin` as credentials. These are configured in two places:

1. **Docker Compose** (infrastructure setup):
   `infrastructure/docker/docker-compose.startup.yaml` - defines the database credentials when the container starts

2. **Service properties** (application config):
   `config/dev/*.properties` - services use `${DB_POSTGRES_USERNAME}` and `${DB_POSTGRES_PASSWORD}` environment variables

To change the credentials:
1. Update the Docker Compose file with your new credentials
2. Set the corresponding environment variables before starting services

### Production Environment Variables

For production, these additional variables are needed:

```bash
export POSTGRES_HOST=your-postgres-host
export DB_POSTGRES_USERNAME=your-user
export DB_POSTGRES_PASSWORD=your-password
export KEYCLOAK_URL=https://your-keycloak-url
export EUREKA_URL=http://your-eureka-url/eureka/
export ZIPKIN_URL=http://your-zipkin-url
```

## 6. Start Services

Start in this order:

```bash
# 1. Config Server
./run-service.sh config-service

# 2. Eureka (Service Discovery)
./run-service.sh eureka-service

# 3. Other services (any order)
./run-service.sh user-service
./run-service.sh event-service
# ... etc
```

By default, services run with the `dev` profile. To run with production config:

```bash
SPRING_PROFILES_ACTIVE=prod ./run-service.sh user-service
```

## 7. Observability Stack

The infrastructure starts four observability tools automatically with `docker-compose`:

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

> **This requires `LOG_PATH` to be set** (step 4) so Promtail can find the log files.
> If `LOG_PATH` is not set, logs will still appear in the IntelliJ console but
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
are accessible without authentication (by design — see `ActuatorSecurityConfig`):

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Service health (UP/DOWN), used by load-balancers and K8s probes |
| `/actuator/info` | App version and build metadata |
| `/actuator/prometheus` | All Micrometer metrics in Prometheus format |

All other actuator endpoints (`/actuator/env`, `/actuator/beans`, `/actuator/loggers`, etc.)
are **denied** — they leak internal configuration or allow runtime changes.

## 8. SonarQube (Optional)

For code quality analysis:

1. Open SonarQube: http://localhost:9000
2. Login with `admin` / `admin` (change password on first login)
3. Generate a token: **My Account** → **Security** → **Generate Tokens**
4. Run analysis:

```bash
./infrastructure/sonarqube/run-sonar.sh <your-token>
```

## Test Users

| Username | Password | Role | Group |
|----------|----------|------|-------|
| admin | admin123 | employee | employees |
| john.doe | customer123 | customer | customers |
| jane.smith | customer123 | customer | customers |
| carlos.garcia | customer123 | customer | customers |

## OAuth2 Token (Postman)

```
POST http://localhost:8180/realms/booking-platform/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=booking-app&username=john.doe&password=customer123
```
