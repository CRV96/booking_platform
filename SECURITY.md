# Security Policy

## Supported Versions

Releases are date-based (e.g. `v2026.09.01`). Security fixes are applied to the **latest release only**.

| Version | Supported |
|---------|-----------|
| Latest release (`main`) | ✅ |
| Older / archived releases | ❌ |

If you are running an older version, upgrade before reporting. See the [Releases](../../wiki/releases) guide for the upgrade process.

---

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report security issues by email to: **dev@robertciapa.com**

Please include:
- A clear description of the vulnerability
- Steps to reproduce or a proof-of-concept
- The affected component(s) and version
- Your assessment of impact and severity

You can expect an acknowledgement within **3 business days** and a status update within **7 business days**. If the issue is confirmed, a fix will be prioritised based on severity. You will be credited in the release notes unless you prefer otherwise.

---

## Security Model

### Authentication & Identity

- **Keycloak** is the OAuth2/OIDC identity provider. User credentials are never stored by the application itself.
- **JWT access tokens** (short-lived) are validated at the GraphQL gateway on every request. Downstream gRPC services receive the validated user context via gRPC metadata — they do not re-validate the token.
- **Refresh tokens** are stored client-side only and invalidated server-side on logout via the Keycloak token revocation endpoint.
- **Email verification** is enforced by Keycloak before account activation. Unverified accounts older than 7 days are purged nightly.

### Transport Security

- **nginx** sits in front of the GraphQL gateway in Docker deployments. It stamps the real client IP (`X-Forwarded-For`) before forwarding, preventing IP spoofing for rate limit bypass.
- **mTLS** (mutual TLS) is available for gRPC channels between services. Enabled in production; optional in development.
- All inter-service gRPC calls carry the JWT of the originating user, forwarded by the gateway.

### Authorisation

Three roles are enforced at the GraphQL resolver level:

| Role | Permissions |
|------|-------------|
| `customer` | Own bookings, own tickets, own profile, public event browsing |
| `employee` | All customer permissions + event management, ticket validation/cancellation |
| `admin` | All employee permissions + user search and management |

Mutations that mutate another user's data (e.g. viewing all users, validating a ticket) verify the caller's role before executing. Role claims are sourced from Keycloak's `realm_access.roles` JWT claim.

### Rate Limiting

The GraphQL gateway enforces Redis-backed sliding-window rate limits per user tier:

| Tier | Applies to |
|------|------------|
| Anonymous | Unauthenticated requests |
| Authenticated | Logged-in users (all roles) |
| Search | Event search queries specifically |

Rate limit counters are keyed by the real client IP (stamped by nginx) or JWT subject, depending on the endpoint. Exceeding the limit returns `HTTP 429`.

### Secrets & Configuration

- All secrets (database credentials, Keycloak client secrets, Stripe keys) are injected via environment variables from a `.env` file that is gitignored and never committed.
- The `.env` file ships with intentionally weak local-development defaults. **These must be changed before any shared or internet-facing deployment.**
- Config files in `config/dev/` and `config/prod/` use `${ENV_VAR:default}` placeholders so secrets are resolved at runtime from the environment, not baked into config.
- Keycloak client secrets should be regenerated via the Keycloak Admin Console if a deployment is exposed beyond a local machine.

### Actuator Lockdown

Spring Boot Actuator endpoints are restricted. Only three are publicly accessible without authentication:

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Liveness / readiness probe |
| `/actuator/info` | Build metadata |
| `/actuator/prometheus` | Metrics scraping (Prometheus) |

All other actuator endpoints are disabled or access-controlled.

### Dependency Security

- Dependencies are managed centrally in the root `pom.xml` using Spring Boot's dependency BOM for consistent, vetted versions.
- SonarCloud static analysis runs on every push to `main` and flags known vulnerability patterns.
- Dependency updates should be reviewed regularly and applied promptly when security advisories are published.

---

## Known Limitations (Development Defaults)

The following are intentional simplifications for local development that **must not be used in production**:

| Limitation | Production recommendation |
|------------|--------------------------|
| `admin`/`admin` credentials for PostgreSQL, MongoDB, Keycloak | Rotate to strong random credentials |
| `sk_test_*` Stripe key | Replace with production key managed via secrets manager |
| mTLS disabled by default | Enable `GRPC_MTLS_ENABLED=true` and distribute valid certificates |
| HTTP (no TLS) on nginx | Terminate TLS at the load balancer or add an HTTPS server block |
| MailHog (no real SMTP) | Configure a real SMTP relay with authentication |
