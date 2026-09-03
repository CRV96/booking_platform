# Configuration (`config/`)

All service configuration lives in one place — the `config/` folder — and is served to every service on boot by [config-service](config-service) (Spring Cloud Config). This page explains the layout, the placeholder pattern that makes one set of files work across every environment, and the pitfalls to avoid.

## Layout

```
config/
├── dev/            # development profile
│   ├── graphql-gateway.properties
│   ├── user-service.properties
│   ├── event-service.properties
│   ├── booking-service.properties
│   ├── payment-service.properties
│   ├── notification-service.properties
│   ├── analytics-service.properties
│   ├── ticket-service.properties
│   └── eureka-service.properties
└── prod/           # production profile — same files, hardened values
```

One file per service per profile. config-service serves `config/<profile>/<service>.properties` to the matching service based on its application name and active Spring profile. (config-service and eureka-service also hold minimal local config to bootstrap themselves.)

## The placeholder pattern

Properties never hard-code environment-specific values. They use `${ENV_VAR:default}`:

```properties
grpc.client.user-service.address=${GRPC_CLIENT_USER_SERVICE_ADDRESS:static://localhost:9091}
grpc.client.security.enabled=${GRPC_MTLS_ENABLED:true}
```

config-service passes the **placeholder itself** through to the client unchanged; the **client** resolves it from its own environment. So the same file yields:

- **Local (host):** the `:default` after the colon — e.g. `static://localhost:9091`, mTLS on.
- **Docker:** env vars set in the compose files — container DNS names, mTLS off.
- **Kubernetes:** env vars from ConfigMaps/Secrets — service DNS names, mTLS off.

Secrets (DB passwords, Keycloak client secrets, Stripe keys) are **always** placeholders resolved from a gitignored `.env` (local) or Kubernetes Secrets — never literals in `config/`. See [Security](https://github.com/CRV96/booking_platform/blob/main/SECURITY.md) and [Installation → Environment Variables](INSTALLATION).

## dev vs prod

`config/dev/` favours convenience (GraphiQL and schema introspection enabled, verbose logging, local hosts). `config/prod/` hardens the same keys (introspection off, stricter logging, external hosts). The active profile selects the folder.

## Two things that bite

1. **`spring.config.import` is mandatory.** Every config-client service needs the config-server import with a placeholder, e.g. `optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`. Without it a service **cannot reach config-service inside Docker** and silently falls back to whatever local properties it has — a classic "works locally, broken in Docker" bug. The gateway uses the `optional:` prefix so it still starts if config-service is briefly down.

2. **Config-server properties win.** Values served by config-service override local system properties and most env vars by default. To override a served value in Docker/K8s you must add a `${ENV_VAR:...}` placeholder in the `config/` file — a bare `-D` flag or env var will not reliably override it. Hyphenated map keys (like `grpc.client.user-service.address`) also can't be overridden by relaxed-binding env var names; use placeholders.

## Related

- [config-service](config-service) · [Installation → Config Server](INSTALLATION) · [Docker deployment](docker-deployment) · [Kubernetes deployment](kubernetes-deployment)
