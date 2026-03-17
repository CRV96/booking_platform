## Event Booking Platform

A production-grade microservices platform for event booking, built with Java 21 and Spring Boot 3.4. The system covers the full lifecycle: user registration, event management, seat reservation with distributed locking, payment processing, ticket generation, email notifications, and real-time analytics.

---

## Architecture

- **10 microservices** communicating via gRPC with optional mTLS, orchestrated through a GraphQL API gateway
- **Event-driven** workflows powered by Apache Kafka — booking, payment, ticketing, and notification flows are fully decoupled
- **Centralized configuration** via Spring Cloud Config Server with per-environment properties (`dev`/`prod`)
- **Service discovery** via Spring Cloud Netflix Eureka

## Services

| Service | Purpose |
|---------|---------|
| **GraphQL Gateway** | Single API entry point with JWT authentication and per-tier rate limiting (anonymous, authenticated, search) |
| **User Service** | Registration, login, profile management — delegates identity to Keycloak (OAuth2/OIDC) |
| **Event Service** | Event CRUD with seat categories, draft/publish/cancel workflow |
| **Booking Service** | Seat reservation with Redis distributed locking and idempotency keys to prevent double-booking |
| **Payment Service** | Payment processing and refunds using the transactional outbox pattern for guaranteed event delivery |
| **Ticket Service** | Ticket generation and validation, stored in Redis |
| **Notification Service** | Kafka consumer that sends confirmation/cancellation emails |
| **Analytics Service** | Real-time booking and revenue metrics aggregated from domain events |
| **Config Service** | Centralized configuration server (Spring Cloud Config, native filesystem) |
| **Eureka Service** | Service registry and discovery |

## Shared Modules

- **common-proto** — Protobuf/gRPC service definitions shared across all services
- **common-core** — Shared DTOs, exception handling, security configuration, actuator config
- **common-grpc-security** — JWT interceptors for gRPC (server and client side)
- **common-events** — Kafka event schemas for booking, event, and payment domains

## Security

- **Keycloak** as OAuth2/OIDC identity provider with pre-configured realm, clients, roles, and test users
- **JWT validation** at the gateway and propagated to downstream services via gRPC metadata
- **mTLS** (optional) for mutual authentication on gRPC channels
- **Rate limiting** at the gateway — configurable per user tier with Redis-backed sliding windows
- **Actuator lockdown** — only health, info, and Prometheus endpoints are publicly accessible

## Data & Messaging

- **PostgreSQL** for user, booking, and payment data with Flyway schema migrations
- **MongoDB** for event and analytics document storage
- **Redis** for distributed locks, idempotency guards, ticket storage, and rate-limit counters
- **Apache Kafka** (KRaft mode) for event streaming with dead letter topics for failed messages

## Resilience

- **Resilience4j** circuit breakers, retries, bulkheads, and time limiters on inter-service calls
- **Transactional outbox** in payment-service for exactly-once event publishing
- **Distributed locking** with automatic expiry to prevent seat overselling
- **Dead letter topics** for failed Kafka messages — no silent data loss

## Observability

- **Prometheus** scrapes metrics from all services via Micrometer
- **Grafana** with pre-configured dashboards for metrics, logs, and traces
- **Loki + Promtail** for centralized log aggregation with structured JSON logging (LogstashEncoder)
- **Zipkin** for distributed tracing across synchronous (gRPC) and asynchronous (Kafka) calls
- **Correlation IDs** propagated through gRPC metadata and Kafka headers for end-to-end request tracing

## DevOps & Tooling

- **Docker Compose** — full-stack deployment (infrastructure + all services) with a single command
- **Multi-stage Dockerfile** — shared across all services, parameterized by `SERVICE_NAME`
- **GitHub Actions CI pipeline** — Build → Test → Docker Build → SonarQube Analysis
- **SonarCloud** integration for continuous code quality and test coverage tracking
- **JaCoCo** for test coverage reporting across all modules
- **Testcontainers** for integration tests with real PostgreSQL, MongoDB, Redis, and Kafka instances
- **Postman collections** for quick API testing (local and Docker environments)

## Developer Experience

- **`run-service.sh`** script with per-service debug ports (5005–5014), suspend mode, and optional 1Password integration
- **Dev tools in Docker** — Mongo Express, RedisInsight, MailHog (email testing), Kafka UI
- **Local SonarQube** instance for code quality analysis during development
- **Centralized config** with environment variable overrides — no hardcoded secrets in source

## Infrastructure at a Glance

| Component | Port | Purpose |
|-----------|------|---------|
| GraphQL Gateway | 8080 | API entry point |
| Keycloak | 8180 | Identity provider |
| PostgreSQL | 5432 | Relational storage |
| MongoDB | 27017 | Document storage |
| Redis | 6379 | Cache, locks, tickets |
| Kafka | 9092 | Event streaming |
| Grafana | 3000 | Dashboards |
| Prometheus | 9090 | Metrics |
| Zipkin | 9411 | Tracing |
| MailHog | 8025 | Email testing |
| Kafka UI | 8085 | Topic browser |

---

## Getting Started

```bash
git clone <repository-url>
cd booking-platform

# Full stack in Docker
docker compose -f infrastructure/docker/docker-compose.yaml up --build -d

# GraphQL playground
open http://localhost:8080/graphql
```

See [INSTALLATION.md](../INSTALLATION.md) for detailed setup instructions including local development, environment variables, Keycloak configuration, and observability stack.
