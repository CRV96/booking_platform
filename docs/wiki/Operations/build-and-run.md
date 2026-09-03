# Build and Run

Three ways to run the platform, plus the helper scripts for building and running individual services on the host. For first-time setup detail (`.env`, Keycloak, certificates, semantic search) follow the full **[Installation](INSTALLATION)** guide — this page is the quick operational reference.

## Prerequisites

- **JDK 21** (Temurin recommended)
- **Docker** (for the backing services, or the whole stack)
- **Node 20+** for the [frontend](frontend-guide)
- Maven is provided by the wrapper (`./mvnw`) — no local install needed

## Three ways to run

| Mode | Command | Use when |
|------|---------|----------|
| **Full Docker** | `docker compose -f infrastructure/docker/docker-compose.yaml up --build -d` | You want everything running with one command |
| **Services on host** | infra in Docker + `./start-all.sh` | You're developing a service and want fast rebuilds/debug |
| **Kubernetes** | `./infrastructure/k8s/run.sh` | Production-shaped local deployment |

Details: [Docker deployment](docker-deployment) · [Kubernetes deployment](kubernetes-deployment).

## Building

The project is a Maven multi-module build driven by the wrapper.

```bash
./mvnw clean install                 # build everything (runs tests)
./mvnw clean install -DskipTests     # build everything, skip tests
./mvnw -pl services/booking-service -am package   # one service + its module deps
```

Java 25 hosts must pass `-Dnet.bytebuddy.experimental=true` to Surefire for the Mockito tests.

### `build-service.sh`

A convenience wrapper for module builds:

```bash
./build-service.sh user-service              # build one service
./build-service.sh user-service --with-deps  # + its module dependencies (-am)
./build-service.sh all --clean --tests       # clean build all modules with tests
```

## Running services on the host

For iterative development, run the backing services in Docker and the app services on the host (hot rebuild, debugger, no image builds):

```bash
# 1. Backing services only
docker compose -f infrastructure/docker/docker-compose.startup.yaml up -d

# 2. mTLS certs (config/dev defaults GRPC_MTLS_ENABLED=true)
./infrastructure/certs/generate-certs.sh      # or set GRPC_MTLS_ENABLED=false

# 3a. All services, in the right order, in a tmux session
./start-all.sh                # add --debug for remote debugging on each

# 3b. …or one at a time (separate terminals)
./run-service.sh config-service     # must be first
./run-service.sh eureka-service     # second
./run-service.sh booking-service    # then any others
```

- **`run-service.sh <service> [--debug]`** — sources `.env`, sets the dev profile, and runs one Spring Boot service on the host.
- **`start-all.sh [--debug]`** — launches every service in its own tmux window and **waits for each one's `/actuator/health` to report `UP`** before starting the next, guaranteeing `config → eureka → everything → gateway` order.

## Frontend

```bash
cd frontend
npm install --legacy-peer-deps    # Angular 22 + Apollo 4 peer ranges
npm start                          # dev server at http://localhost:4200
```

Calls are proxied to the gateway via `proxy.conf.json`. Full walkthrough: [Frontend guide](frontend-guide).

## Related

- [Installation](INSTALLATION) · [Releases](releases) · [CI/CD](ci-cd) · [Using the app](using-the-app)
