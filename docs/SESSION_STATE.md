# Booking Platform - Session State

**Last Updated:** 2026-01-28

## Project Overview

Microservices booking platform for learning purposes. Spring Boot 3.4.1 / Spring Cloud 2024.0.0 / Java 21.

---

## Completed Work

### Phase 1: Foundation (COMPLETE)

#### Chapter 1: Infrastructure & Configuration ✅
- Docker Compose with all infrastructure (Postgres, Redis, Kafka, MongoDB, Keycloak, Zipkin, SonarQube, MailHog)
- Config Server serving properties from `/config` folder
- All service configs in place (`*-service-dev.properties`)

#### Chapter 2: Service Discovery ✅
- Eureka Server running on port 8761
- All 8 services registered and UP:
  - graphql-gateway (8080)
  - user-service (8081)
  - event-service (8082)
  - booking-service (8083)
  - payment-service (8084)
  - ticket-service (8085)
  - notification-service (8086)
  - analytics-service (8087)

#### Chapter 3: Security Foundation ✅
- Keycloak running on port 8180
- Realm: `booking-platform`
- Roles: `employee`, `customer`
- Groups: `employees`, `customers` (with role mapping)
- Users:
  - `admin` / `admin123` (employee group)
  - `john.doe` / `customer123` (customers group)
  - `jane.smith` / `customer123` (customers group)
  - `carlos.garcia` / `customer123` (customers group)
- OAuth2 Client: `booking-app` (public, for Postman/frontend login)
- JWT tokens include: roles, groups, phone_number, country
- Token endpoint tested and working

---

## Current Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     INFRASTRUCTURE                               │
├─────────────────────────────────────────────────────────────────┤
│ PostgreSQL (5432)  │ MongoDB (27017)  │ Redis (6379)            │
│ Kafka (9092)       │ Keycloak (8180)  │ Zipkin (9411)           │
│ SonarQube (9000)   │ MailHog (8025)   │                         │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     SPRING CLOUD                                 │
├─────────────────────────────────────────────────────────────────┤
│ Config Server (8888)  →  Serves configs to all services         │
│ Eureka Server (8761)  →  Service discovery registry             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     MICROSERVICES (all registered with Eureka)   │
├─────────────────────────────────────────────────────────────────┤
│ graphql-gateway (8080)    │ user-service (8081)                 │
│ event-service (8082)      │ booking-service (8083)              │
│ payment-service (8084)    │ ticket-service (8085)               │
│ notification-service (8086) │ analytics-service (8087)          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Service Dependencies (Current - Minimal)

All services have only these dependencies:
- `spring-boot-starter-web` (from parent)
- `spring-boot-starter-actuator` (from parent)
- `spring-boot-starter-validation` (from parent)
- `spring-cloud-starter-netflix-eureka-client`
- `spring-cloud-starter-config`
- `lombok` (from parent)

Dependencies are added as features are implemented.

---

## Key Files

| File | Purpose |
|------|---------|
| `infrastructure/docker/docker-compose.startup.yaml` | All infrastructure |
| `infrastructure/keycloak/realm/booking-platform-realm.json` | Keycloak realm config |
| `infrastructure/keycloak/realm/master-realm.json` | Master realm (SSL disabled) |
| `infrastructure/docker/postgres/init-multiple-dbs.sh` | Creates userdb |
| `config/*-dev.properties` | Service configurations |
| `pom.xml` | Parent POM with common dependencies |
| `run-service.sh` | Helper to run services with 1Password |
| `infrastructure/sonarqube/run-sonar.sh` | Run SonarQube analysis |

---

## Startup Order

1. `docker-compose -f infrastructure/docker/docker-compose.startup.yaml up -d`
2. `./run-service.sh config-service` (port 8888)
3. `./run-service.sh eureka-service` (port 8761)
4. Other services in any order

---

## Testing OAuth2 (Postman)

**Get Token:**
```
POST http://localhost:8180/realms/booking-platform/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=booking-app&username=john.doe&password=customer123
```

---

## Next Steps (Phase 2)

### Chapter 4: User Service
- JPA/PostgreSQL integration
- CRUD operations for user profiles
- gRPC service implementation

### Chapter 5: Event Service
- MongoDB integration
- Redis caching
- Event/venue management

### Chapter 6: API Gateway
- GraphQL schema
- JWT validation with Keycloak
- Route to backend services

---

## Development Roadmap

| Phase | Chapters | Status |
|-------|----------|--------|
| Phase 1: Foundation | Ch 1-3 (Config, Eureka, Security) | ✅ COMPLETE |
| Phase 2: Core Services | Ch 4-6 (User, Event, Gateway) | 🔜 NEXT |
| Phase 3: Business Logic | Ch 7-9 (Booking, Payment, Ticket) | ⏳ Pending |
| Phase 4: Communication | Ch 10-11 (Kafka, Notifications) | ⏳ Pending |
| Phase 5: Observability | Ch 12-13 (Tracing, Analytics) | ⏳ Pending |

---

## Notes

- Services POMs are minimal - add dependencies as needed per chapter
- Keycloak uses `userdb` database (shared with user-service)
- 1Password integration via `op run --env-file=".env"` in scripts
- Java 21 required (scripts set JAVA_HOME)
