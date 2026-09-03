# Services Overview

The platform is a Maven multi-module project with **ten** Spring Boot services: two **platform** services (config, discovery), one **gateway**, and seven **domain** services. Each domain service owns its data store and exposes a gRPC API; none is reachable directly from the browser.

## The ten services

| Service | HTTP | gRPC | Store | One-line role |
|---------|------|------|-------|---------------|
| [config-service](config-service) | 8888 | — | — | Serves per-environment properties to every service on boot |
| [eureka-service](eureka-service) | 8761 | — | — | Service registry for name-based discovery |
| [graphql-gateway](graphql-gateway) | 8080 | — | Redis | The single client API; GraphQL → gRPC, auth, rate limiting |
| [user-service](user-service) | 8081 | 9091 | PostgreSQL `userdb` | Auth + profiles, backed by Keycloak |
| [event-service](event-service) | 8082 | 9093 | MongoDB `eventdb` | Events, publishing, keyword + semantic search |
| [booking-service](booking-service) | 8083 | 9094 | PostgreSQL `bookingdb` + Redis | Seat reservation, cart, lovelist |
| [payment-service](payment-service) | 8084 | 9095 | PostgreSQL `paymentdb` | Payments, refunds, outbox |
| [ticket-service](ticket-service) | 8088 | 9096 | MongoDB `ticketdb` | Ticket issue / validate / cancel |
| [notification-service](notification-service) | 8086 | — | — | Sends email from Kafka events |
| [analytics-service](analytics-service) | 8087 | 9097 | MongoDB `analyticsdb` | Booking/revenue metrics, REST API |

## Startup order

Services depend on config and discovery being up first:

1. **config-service** — everything else fetches its properties from here.
2. **eureka-service** — services register so they can find each other.
3. **Everything else** — in any order; gRPC clients and Kafka consumers retry until dependencies are reachable.

The [`start-all.sh`](build-and-run) script and the Docker/Kubernetes deployments all encode this ordering.

## What every service shares

All services are built on the same foundations from the [shared modules](shared-modules):

- **Config on boot** from [config-service](config-service) via `spring.config.import` (see [Configuration](configuration)).
- **Structured logging** with correlation IDs (`common-core`) flowing to Loki (see [Observability](observability)).
- **Actuator** exposing `/actuator/health`, `/info`, and `/prometheus` only (`common-security` locks the rest down).
- **gRPC interceptors** for JWT context, correlation-id propagation, and exception mapping (`common-security`).
- **Protobuf contracts** for gRPC (`common-proto`) and Kafka payloads (`common-events`).

## Anatomy of a domain service

The domain services follow a consistent package layout, so once you know one you can read them all:

| Package | Contains |
|---------|----------|
| `grpc` | gRPC server (`@GrpcService`) — the service's public API |
| `service` | Business logic (interface + `impl`) |
| `repository` | Spring Data repositories (JPA or Mongo) |
| `entity` / `document` | Persistence models |
| `dto` / `mapper` | Internal DTOs and proto ↔ model mappers |
| `messaging` | Kafka producers and `@KafkaListener` consumers |
| `validation` / `validator` | Input and business-rule checks |
| `config` / `properties` | Spring config and typed `@ConfigurationProperties` |
| `scheduler` | `@Scheduled` background jobs (ShedLock-guarded) |
| `constants` | Field names, group ids, and other shared literals |

Read the per-service pages for the specifics of each. Cross-cutting request flows are in [Application flows](application-flows).
