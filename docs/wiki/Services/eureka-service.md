# eureka-service

**Spring Cloud Netflix Eureka** service registry. Lets services find each other by logical name rather than hard-coded host/port, which matters when addresses differ between local, Docker, and Kubernetes.

| Property | Value |
|----------|-------|
| HTTP port | 8761 (also the dashboard) |
| gRPC | none |
| Store | in-memory registry |
| Depends on | [config-service](config-service) — **starts second** |

## What it does

- Each service **registers** itself on startup and sends heartbeats.
- Clients **resolve** peers by name. The gRPC clients (`net.devh` starter) fall back to Eureka's `DiscoveryClientNameResolver` when no static address is configured for a channel — so in environments without fixed gRPC hosts, discovery still works.
- The **dashboard** at `http://localhost:8761` lists every registered instance and its health — a quick way to confirm the platform came up cleanly.

## Notes

- In Docker and Kubernetes, most gRPC channels use **static** addresses (container/service DNS names), so Eureka is primarily a health/inventory view there; on the host it also backs name resolution.
- Like every service, it fetches its own config from [config-service](config-service).

## Related

- [config-service](config-service) — starts first.
- [Communication patterns](communication-patterns) — how gRPC clients resolve targets.
