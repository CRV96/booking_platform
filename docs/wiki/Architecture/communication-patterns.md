# Communication Patterns

Three protocols carry all traffic in the platform. This page explains what each is for, how requests are authenticated, and how a single logical request is traced across every hop.

## 1. GraphQL — client to gateway

The Angular SPA speaks **only GraphQL**, to a single endpoint on the [graphql-gateway](graphql-gateway):

- `POST /graphql` — queries and mutations
- `GET /graphiql` — in-browser playground (dev)

The gateway hosts one schema, assembled from per-domain schema files (`*-schema.graphqls`): user, event, booking, cart, lovelist, payment, ticket. Each field is backed by a resolver (`@QueryMapping` / `@MutationMapping` / `@SchemaMapping`) that calls one or more gRPC services and maps the protobuf response into a GraphQL DTO.

A useful consequence: **the backend can support a field the client never sees until the gateway threads it through** — a proto field must be added to the gateway DTO, schema, and client mapping before it reaches the browser. Lazy relationships (for example a booking's live `event`) are hydrated with `@SchemaMapping` only when the query asks for them.

Authentication: the client sends `Authorization: Bearer <JWT>`. The gateway validates the token once, then forwards the user context downstream — services do not re-validate.

## 2. gRPC — gateway to service, and service to service

Every domain service exposes a gRPC server; the gateway (and some services) hold gRPC clients. Contracts are protobuf files in [`common-proto`](shared-modules), compiled to Java at build time.

| Service | gRPC port | Proto |
|---------|-----------|-------|
| user-service | 9091 | `user/auth_service.proto`, `user/user_service.proto` |
| event-service | 9093 | `event/event_service.proto` |
| booking-service | 9094 | `booking/booking_service.proto`, `cart.proto`, `lovelist.proto` |
| payment-service | 9095 | `payment/payment_service.proto` |
| ticket-service | 9096 | `ticket/ticket_service.proto` |
| analytics-service | 9097 | (consumes events; gRPC reserved) |

The stack is `net.devh` grpc-spring-boot-starter. Cross-cutting concerns are implemented as **interceptors** in [`common-security`](shared-modules):

- **JWT propagation (client) / context (server)** — the caller's JWT rides in gRPC metadata; the server interceptor extracts the user id into `GrpcUserContext` for the request scope.
- **Correlation-id interceptors** — propagate the request's correlation id in metadata (see below).
- **Exception mapping** — `ServiceException` subtypes are translated to gRPC status codes, and back into GraphQL errors at the gateway.

**Transport security:** channels can run mutual TLS. `GRPC_MTLS_ENABLED` toggles it — `config/dev` defaults to `true` (certificates required; generate them with `infrastructure/certs/generate-certs.sh`), while Docker and Kubernetes run plaintext inside their private networks. See [Installation → mTLS](INSTALLATION).

## 3. Apache Kafka — asynchronous domain events

Domain events decouple the write path from everything that reacts to it. Payloads are **protobuf** (schemas in [`common-events`](shared-modules) under `proto/events/`), serialized by a shared `ProtobufSerializer`.

### Topics

Defined once in `common-events` `KafkaTopics`:

| Topic | Published by | Consumed by |
|-------|--------------|-------------|
| `events.event.created` | event-service | notification, event (vector index) |
| `events.event.updated` | event-service | notification, event (vector index) |
| `events.event.published` | event-service | notification, event (vector index) |
| `events.event.cancelled` | event-service | notification, event (vector index) |
| `events.booking.created` | booking-service | notification |
| `events.booking.confirmed` | booking-service | ticket, notification, analytics |
| `events.booking.cancelled` | booking-service | ticket, notification, analytics |
| `events.payment.completed` | payment-service | booking, analytics |
| `events.payment.failed` | payment-service | booking, notification |
| `events.payment.refund-completed` | payment-service | booking |

The canonical fan-out: **booking-service publishes `events.booking.confirmed`**, and independently ticket-service issues QR tickets, notification-service emails the customer, and analytics-service updates metrics — none of them know about the others.

### Dead-letter topics

Every consumer is configured so a message that keeps failing after retries is routed to `<topic>-dlt` (the `-dlt` suffix is a shared constant) rather than being lost or blocking the partition. DLT messages can be inspected in Kafka UI (`http://localhost:8085`) and replayed.

### Exactly-once publish — the outbox

payment-service must publish `payment.*` events **if and only if** the payment row committed. It writes the event into an `outbox_events` table in the same transaction, and `OutboxPollingPublisher` (a ShedLock-guarded `@Scheduled` poller) later reads unpublished rows and sends them to Kafka. A crash between DB commit and publish is recovered on the next poll. See [payment-service](payment-service).

## Correlation IDs — one request, traced everywhere

Each inbound request is assigned a **correlation id** at the gateway. It is then propagated:

- across **gRPC** hops via a metadata interceptor into `CorrelationIdContext`,
- across **Kafka** via producer/consumer interceptors that copy it into message headers,
- into **every log line** through the logging MDC.

The result: filtering logs by one correlation id in Grafana/Loki shows the complete path of a request across all services and event consumers. The plumbing lives in [`common-core`](shared-modules) (context holders) and [`common-events`](shared-modules) (Kafka interceptors). See [Observability](observability) for the LogQL queries.

## Discovery and configuration

- **[eureka-service](eureka-service)** lets services resolve one another by name instead of hard-coded hosts; gRPC clients fall back to Eureka when no static address is configured.
- **[config-service](config-service)** serves each service its properties on boot from `config/<profile>/<service>.properties`. See [Configuration](configuration).
