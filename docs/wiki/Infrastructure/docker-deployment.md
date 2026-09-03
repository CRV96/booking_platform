# Docker Deployment

The Docker Compose stack is the default way to run the whole platform — every backing service, all ten application services, and the observability tooling — with one command.

## One command

```bash
docker compose -f infrastructure/docker/docker-compose.yaml up --build -d
```

The API is then reachable through nginx: GraphQL at `http://localhost/graphql`, GraphiQL at `http://localhost/graphiql`.

## The three compose files

`docker-compose.yaml` is a thin wrapper that `include:`s the other two, so you can bring up the whole stack from it or run the halves independently:

| File | Contains |
|------|----------|
| `docker-compose.yaml` | Wrapper — includes the two below |
| `docker-compose.startup.yaml` | **Infrastructure**: Postgres, MongoDB, Redis, Kafka, Ollama, Keycloak, the observability stack, dev UIs, and the **release-manager** init container |
| `docker-compose.services.yaml` | **Application services** + the nginx reverse proxy |

Run just the infrastructure (useful when developing services on the host — see [Build and run](build-and-run)):

```bash
docker compose -f infrastructure/docker/docker-compose.startup.yaml up -d
```

## The shared Dockerfile

Every service is built from a single multi-stage `Dockerfile.service`, parameterized by a build arg:

```dockerfile
FROM eclipse-temurin:21-jdk AS build
ARG SERVICE_NAME
COPY common common
COPY services services
COPY config config
RUN bash ./mvnw package -pl services/${SERVICE_NAME} -am -DskipTests -q
FROM eclipse-temurin:21-jre
COPY --from=build /app/services/${SERVICE_NAME}/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

- **Stage 1 (build)** compiles just the target service and its module dependencies (`-pl <service> -am`) with the Maven wrapper.
- **Stage 2 (runtime)** ships only the JRE + the built jar — small final image.
- The compose files pass `SERVICE_NAME` per service, so ten services share one Dockerfile. CI builds one service with it as a smoke test (see [CI/CD](ci-cd)).

## Port map

### Application services
| Service | Host port | Notes |
|---------|-----------|-------|
| graphql-gateway | *(none)* | only via nginx :80 |
| user-service | 8081 | gRPC 9091 |
| event-service | 8082 | gRPC 9093 |
| booking-service | 8083 | gRPC 9094 |
| payment-service | 8084 | gRPC 9095 |
| notification-service | 8086 | — |
| analytics-service | 8087 | gRPC 9097 |
| ticket-service | 8088 | gRPC 9096 |
| eureka-service | 8761 | dashboard |
| config-service | *(internal)* | 8888 |
| nginx | 80 | public edge |

### Infrastructure & tooling
| Component | Host port | Purpose |
|-----------|-----------|---------|
| PostgreSQL | 5432 | userdb, bookingdb, paymentdb |
| MongoDB (atlas-local) | 27017 | eventdb, ticketdb, analyticsdb, `$vectorSearch` |
| Redis | 6379 | locks, cache, rate limit |
| Kafka | 9092 | event streaming (KRaft) |
| Ollama | 11434 | local embedding model |
| Keycloak | 8180 | identity (admin console) |
| Zipkin | 9411 | traces |
| Prometheus | 9090 | metrics |
| Grafana | 3000 | dashboards |
| Loki | 3100 | log store |
| Mongo Express | 8090 | Mongo UI |
| RedisInsight | 5540 | Redis UI |
| MailHog | 1025 / 8025 | SMTP / web UI |
| Kafka UI | 8085 | topic browser (incl. DLTs) |
| SonarQube | 9000 | local code quality |

> Keycloak is published on **8180** (host) → 8080 (container). The gateway is intentionally unpublished — reach it via nginx on port 80.

## Databases and init

- `postgres/init-multiple-dbs.sh` creates `userdb`, `bookingdb`, `paymentdb` in the shared Postgres.
- MongoDB uses the `mongodb-atlas-local` image so `$vectorSearch` works locally for [semantic search](event-service).
- `init-scripts/kafka-init.sh` pre-creates the `events.*` topics and their `-dlt` partners.
- The **release-manager** init container applies versioned Keycloak changes on startup — see [Releases](releases).

## Related

- [Infrastructure overview](infrastructure-overview) · [Build and run](build-and-run) · [Installation](INSTALLATION) · [Observability](observability)
