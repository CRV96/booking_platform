# Booking Platform

A production-grade event booking platform built with Java 21 and Spring Boot microservices. The system handles user registration, event management, seat booking with distributed locking, payment processing, ticket generation, email notifications, and real-time analytics — all connected through gRPC, Kafka, and a GraphQL API gateway.

## Architecture

```
                                    ┌─────────────────────────────────┐
                                    │          GraphQL Gateway        │
                                    │           :8080                 │
                                    │  (Rate Limiting, JWT Auth,      │
                                    │   Request Routing)              │
                                    └───────────┬─────────────────────┘
                                                │ gRPC
                 ┌──────────────┬───────────────┼───────────────┬──────────────┬──────────────┐
                 │              │               │               │              │              │
          ┌──────▼──────┐ ┌────▼────────┐ ┌────▼────────┐ ┌────▼────────┐ ┌───▼───────┐ ┌───▼─────────┐
          │   User      │ │   Event     │ │  Booking    │ │  Payment    │ │  Ticket   │ │  Analytics  │
          │  Service    │ │  Service    │ │  Service    │ │  Service    │ │  Service  │ │  Service    │
          │   :8081     │ │   :8082     │ │   :8083     │ │   :8084     │ │   :8088   │ │   :8087     │
          │  gRPC:9091  │ │  gRPC:9093  │ │  gRPC:9094  │ │  gRPC:9095  │ │ gRPC:9096 │ │  gRPC:9097  │
          └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └─────┬─────┘ └──────┬──────┘
                 │               │               │               │               │              │
          ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐        │        ┌─────▼───────┐
          │ PostgreSQL  │ │  MongoDB    │ │ PostgreSQL  │ │ PostgreSQL  │        │        │  MongoDB    │
          │  (userdb)   │ │             │ │ (bookingdb) │ │ (paymentdb) │        │        │             │
          └─────────────┘ └─────────────┘ └──────┬──────┘ └─────────────┘        │        └─────────────┘
                                                 │                               │
                                           ┌─────▼─────--┐                       │
                                           │   Redis     │◄──────────────────────┘
                                           │ (locks,     │
                                           │  tickets,   │
                                           │  rate-limit)│
                                           └──────────--─┘

                              ┌──────────────────────────────────────┐
                              │             Apache Kafka             │
                              │  (booking.*, event.*, payment.*)     │
                              └────────────┬─────────────────────────┘
                                           │
                              ┌─────────────▼──────────────┐
                              │   Notification Service     │
                              │          :8086             │
                              │  (Email via MailHog/SMTP)  │
                              └────────────────────────────┘

  ┌───────────────┐    ┌───────────────┐
  │ Config Server │    │    Eureka     │
  │    :8888      │    │    :8761      │
  │ (centralized  │    │  (service     │
  │  config)      │    │   discovery)  │
  └───────────────┘    └───────────────┘
```

### Request Flow Example: Creating a Booking

```
Client → GraphQL Gateway (JWT validation + rate limiting)
       → Booking Service (gRPC)
            → Redis: acquire distributed lock for seat category
            → Event Service (gRPC): check seat availability
            → PostgreSQL: persist booking (status: PENDING)
            → Kafka: publish BookingCreated event
                → Payment Service: process payment
                    → Kafka: publish PaymentCompleted event
                        → Booking Service: update status to CONFIRMED
                            → Kafka: publish BookingConfirmed event
                                → Ticket Service: generate ticket (stored in Redis)
                                → Notification Service: send confirmation email
                                → Analytics Service: record booking metrics
            → Redis: release distributed lock
```

## Services

| Service | Port | gRPC | Database | Description |
|---------|------|------|----------|-------------|
| **graphql-gateway** | 8080 | — | Redis | API gateway with GraphQL, JWT authentication, rate limiting |
| **user-service** | 8081 | 9091 | PostgreSQL | User registration/login via Keycloak, profile management |
| **event-service** | 8082 | 9093 | MongoDB | Event CRUD, seat categories, publishing workflow |
| **booking-service** | 8083 | 9094 | PostgreSQL + Redis | Seat reservation with distributed locking, idempotency |
| **payment-service** | 8084 | 9095 | PostgreSQL | Payment processing, refunds, transactional outbox |
| **notification-service** | 8086 | — | — | Email notifications via Kafka consumers |
| **analytics-service** | 8087 | 9097 | MongoDB | Real-time booking/revenue analytics |
| **ticket-service** | 8088 | 9096 | Redis | Ticket generation and validation |
| **config-service** | 8888 | — | — | Centralized configuration (Spring Cloud Config) |
| **eureka-service** | 8761 | — | — | Service discovery (Spring Cloud Netflix Eureka) |

## Technology Stack

| Category | Technologies |
|----------|-------------|
| **Language & Runtime** | Java 21, Spring Boot 3.4, Spring Cloud 2024.0 |
| **API** | GraphQL (Spring GraphQL), gRPC (protobuf), REST (actuator) |
| **Security** | Keycloak (OAuth2/OIDC), JWT, mTLS for gRPC, rate limiting |
| **Databases** | PostgreSQL, MongoDB, Redis |
| **Messaging** | Apache Kafka (KRaft mode) |
| **Resilience** | Resilience4j (circuit breaker, retry, bulkhead, time limiter) |
| **Observability** | Prometheus, Grafana, Loki, Zipkin, Micrometer, structured logging |
| **Code Quality** | JaCoCo, SonarQube/SonarCloud |
| **CI/CD** | GitHub Actions (build, test, Docker build, SonarQube analysis) |
| **Containerization** | Docker, Docker Compose (multi-stage builds) |
| **Schema Management** | Flyway (PostgreSQL migrations) |
| **Build** | Maven (multi-module), Protobuf/gRPC code generation |

## Shared Modules

| Module | Purpose |
|--------|---------|
| `common-proto` | Protobuf/gRPC service definitions shared across services |
| `common-core` | Shared DTOs, exceptions, security config, actuator config |
| `common-grpc-security` | JWT interceptors for gRPC (server + client) |
| `common-events` | Kafka event schemas (booking, event, payment events) |

## Key Patterns & Design Decisions

- **GraphQL Gateway** — Single entry point for all client queries/mutations. Translates GraphQL to gRPC calls, handles authentication, and enforces rate limits per user tier (anonymous, authenticated, search).
- **gRPC for service-to-service** — Binary protocol with strict contracts via protobuf. Optional mTLS for mutual authentication.
- **Event-driven architecture** — Kafka decouples booking, payment, ticket, notification, and analytics flows. Services react to domain events independently.
- **Transactional outbox** — Payment service uses the outbox pattern to guarantee exactly-once event publishing alongside database transactions.
- **Distributed locking** — Redis-based locks prevent double-booking of seats. Combined with idempotency keys to handle retries safely.
- **Centralized configuration** — Spring Cloud Config Server serves environment-specific properties from a local filesystem (`config/dev/`, `config/prod/`).
- **Service discovery** — Eureka enables services to find each other by name instead of hardcoded addresses.
- **Dead Letter Topics (DLT)** — Failed Kafka messages are routed to dead letter topics for investigation instead of being silently dropped.
- **Structured logging with correlation IDs** — Every request gets a correlation ID that propagates across all services via gRPC metadata and Kafka headers, enabling end-to-end request tracing in Grafana/Loki.

## GraphQL API

The gateway exposes a GraphQL endpoint at `http://localhost:8080/graphql`. Available operations:

### Queries
- `me` — Get authenticated user profile
- `user(id)` — Get user by ID (admin only)
- `users(query, page, pageSize)` — Search users (admin only)
- `event(id)` — Get event details (public)
- `events(query, category, city, dateFrom, dateTo, page, pageSize)` — Search events (public)
- `booking(id)` — Get booking details (own bookings)
- `myBookings(page, pageSize, status)` — List own bookings

### Mutations
- `register` / `login` / `logout` / `refreshToken` — Authentication
- `updateProfile` — Update user profile
- `createEvent` / `updateEvent` / `publishEvent` / `cancelEvent` — Event management (employees)
- `createBooking` / `cancelBooking` — Booking operations

### Example: Register and Book

```graphql
# 1. Register
mutation {
  register(input: {
    username: "newuser"
    email: "newuser@example.com"
    password: "password123"
    firstName: "New"
    lastName: "User"
  }) {
    id
    username
  }
}

# 2. Login (returns JWT token)
mutation {
  login(input: {
    username: "newuser"
    password: "password123"
  }) {
    accessToken
    refreshToken
  }
}

# 3. Browse events (public, no auth needed)
query {
  events(city: "Barcelona", pageSize: 5) {
    content {
      id
      name
      date
      venue
      seatCategories { name price availableSeats }
    }
    totalElements
  }
}

# 4. Create booking (requires Authorization: Bearer <token>)
mutation {
  createBooking(input: {
    eventId: "<event-id>"
    seatCategory: "VIP"
    quantity: 2
  }) {
    id
    status
    totalPrice
  }
}
```

## Getting Started

### Quick Start (Docker)

```bash
git clone <repository-url>
cd booking-platform
docker compose -f infrastructure/docker/docker-compose.yaml up --build -d
```

This starts all infrastructure and services. The GraphQL gateway will be available at http://localhost:8080/graphql.

### Full Setup

See **[INSTALLATION.md](INSTALLATION.md)** for detailed instructions including:
- Local development setup (services on host with hot-reload)
- Full Docker deployment
- Environment variables and config server
- Keycloak setup and test users
- mTLS certificate generation
- Observability stack (Grafana, Prometheus, Zipkin)
- SonarQube code quality analysis
- Postman collections for API testing

## Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5432 | Relational data (user, booking, payment) |
| MongoDB | 27017 | Document data (events, analytics) |
| Redis | 6379 | Distributed locks, tickets, rate limiting cache |
| Kafka | 9092 | Event streaming between services |
| Keycloak | 8180 | OAuth2/OIDC identity provider |
| Zipkin | 9411 | Distributed tracing |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3000 | Dashboards (metrics, logs, traces) |
| Loki | 3100 | Log aggregation |
| Mongo Express | 8090 | MongoDB web UI |
| RedisInsight | 5540 | Redis web UI |
| MailHog | 8025 | Email testing UI |
| Kafka UI | 8085 | Kafka topic browser |
| SonarQube | 9000 | Code quality (local) |

## CI Pipeline

GitHub Actions runs automatically on every push to `main` and pull request:

```
Build → Test → Docker Build → SonarQube Analysis
```

- **Build** — Compiles all 14 modules
- **Test** — Runs unit and integration tests with JaCoCo coverage (Testcontainers for database/messaging tests)
- **Docker Build** — Validates the shared Dockerfile builds successfully
- **SonarQube** — Uploads coverage and static analysis to SonarCloud

## Project Structure

```
booking-platform/
├── common/                          # Shared modules
│   ├── common-proto/                #   Protobuf/gRPC definitions
│   ├── common-core/                 #   Shared DTOs, security, exceptions
│   ├── common-grpc-security/        #   gRPC JWT interceptors
│   └── common-events/               #   Kafka event schemas
├── services/
│   ├── config-service/              # Spring Cloud Config Server
│   ├── eureka-service/              # Service Discovery
│   ├── graphql-gateway/             # GraphQL API Gateway
│   ├── user-service/                # User management + Keycloak
│   ├── event-service/               # Event management
│   ├── booking-service/             # Booking with distributed locking
│   ├── payment-service/             # Payment processing + outbox
│   ├── ticket-service/              # Ticket generation
│   ├── notification-service/        # Email notifications
│   └── analytics-service/           # Real-time analytics
├── config/
│   ├── dev/                         # Development properties (per service)
│   └── prod/                        # Production properties
├── infrastructure/
│   ├── docker/                      # Docker Compose files, Dockerfile
│   ├── certs/                       # mTLS certificate generation
│   ├── grafana/                     # Grafana dashboards and datasources
│   ├── prometheus/                  # Prometheus scrape config
│   ├── promtail/                    # Log collection config
│   └── sonarqube/                   # SonarQube analysis script
├── postman/                         # Postman collections for API testing
├── docs/                            # Phase documentation
├── .github/workflows/ci.yml        # CI pipeline
└── pom.xml                          # Root Maven POM (multi-module)
```
