# Shared Modules (`common/`)

Four Maven library modules hold the cross-cutting code every service depends on. Centralizing them keeps contracts and behaviour identical across services and means a change (a new gRPC method, a new Kafka topic, a logging tweak) is made once.

| Module | Purpose |
|--------|---------|
| `common-proto` | Protobuf/gRPC **service** definitions |
| `common-events` | Kafka **event** schemas + serialization + interceptors |
| `common-core` | Logging, error base types, request-context holders, scheduler locking |
| `common-security` | JWT validation, gRPC interceptors, TLS/actuator config, role enums |

## common-proto

The gRPC contracts, under `src/main/proto/`:

- `user/auth_service.proto`, `user/user_service.proto`, `user/common.proto`
- `event/event_service.proto`, `event/common.proto`
- `booking/booking_service.proto`, `booking/cart.proto`, `booking/lovelist.proto`
- `payment/payment_service.proto`
- `ticket/ticket_service.proto`

These compile to Java stubs at build time (the Protobuf/gRPC Maven plugin). The gateway holds the **clients**; each domain service implements the matching **server**. Changing a proto here is the first step of any new backend capability.

## common-events

Everything Kafka:

- **`KafkaTopics`** — the single source of truth for topic names (`events.event.*`, `events.booking.*`, `events.payment.*`) and the `-dlt` suffix.
- **`proto/events/`** — `booking_events.proto`, `event_events.proto`, `payment_events.proto`: the message payloads.
- **Serialization** — `ProtobufSerializer` / `ProtobufDeserializer` for Kafka.
- **Base config** — `BaseKafkaProducerConfig`, `BaseKafkaConsumerConfig` that services extend.
- **Correlation interceptors** — `CorrelationIdKafkaProducerInterceptor` / `...ConsumerInterceptor` copy the correlation id into message headers so traces span the async boundary.

## common-core

Foundational, framework-light utilities:

- **Logging** — `ApplicationLogger`, `LogErrorCode`, `LevelColourConverter` for consistent structured logs across services.
- **Errors** — `ServiceException`, the base type mapped to gRPC status codes (and then GraphQL errors) by the interceptor in `common-security`.
- **Request context** — `GrpcUserContext` (authenticated user id for the request) and `CorrelationIdContext` (the trace id).
- **Scheduling** — `ShedLockConfig`, so `@Scheduled` jobs (cleanup, expiry, outbox) run on only one instance at a time.
- Ships a Spring Boot **auto-configuration** so services get these by just depending on the module.

## common-security

Authentication, transport security, and authorization primitives:

- **JWT** — `JwtValidatorService` (validates tokens against Keycloak), `TokenBlacklistService`, and the `@PublicEndpoint` / `PublicEndpointRegistry` mechanism marking which operations may run unauthenticated.
- **gRPC interceptors** — `JwtContextInterceptor` (server: JWT → `GrpcUserContext`), `JwtPropagationClientInterceptor` (client: forward JWT), `CorrelationIdServerInterceptor` / `CorrelationIdClientInterceptor`, and `GrpcExceptionInterceptor` (map exceptions ↔ status codes). Ordering is fixed by `InterceptorOrder`.
- **Config** — `GrpcServerTlsConfig` (mTLS), `ActuatorSecurityConfig` (locks actuator to health/info/prometheus), `ClockConfig`.
- **Enums** — `Roles` (`customer` / `employee` / `admin`, values lower-case via `getValue()`) and `Keycloak` constants.

> A subtle but important detail: `Roles.EMPLOYEE.getValue()` is `"employee"` (lower-case). Always use `getValue()` for role checks — `name()` would yield `"EMPLOYEE"` and silently reject legitimate users.

## Related

- [Communication patterns](communication-patterns) · [Services overview](services-overview) · [Security model](https://github.com/CRV96/booking_platform/blob/main/SECURITY.md)
