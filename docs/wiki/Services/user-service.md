# user-service

Owns **user identity and profiles**. Authentication is delegated to Keycloak — the application never stores passwords. user-service wraps the Keycloak Admin API for registration/login and keeps profile attributes in its own PostgreSQL database.

| Property | Value |
|----------|-------|
| HTTP port | 8081 |
| gRPC port | 9091 |
| Store | PostgreSQL `userdb` |
| Talks to | Keycloak (admin + token endpoints) |

## gRPC API

Two services (`user/auth_service.proto`, `user/user_service.proto`):

- **Auth:** `Register`, `Login`, `RefreshToken`, `Logout`
- **User:** `GetUser`, `GetUserByUsername`, `GetUserByEmail`, `UpdateUser`, `SearchUsers`, `GetUserEmail`, `GetUsersEmails`

`SearchUsers` is admin-only (enforced at the gateway). `GetUsersEmails` exists so other services (e.g. notification) can resolve recipient addresses.

## Package layout

`config · constants · dto · entity · exception · grpc · init · mapper · messaging · properties · repository · scheduler · service · validation`

- **`entity`** — `UserEntity`, `UserAttributeEntity` (profile attributes such as phone, country, preferred currency/language, and the structured **billing address** stored as JSON).
- **`mapper`** — proto ↔ entity mapping, including the `BillingAddressCodec` that serializes the billing address to/from a single Keycloak attribute.
- **`init`** — Keycloak realm/attribute bootstrap helpers.
- **`scheduler`** — `UnverifiedUserCleanupScheduler`.

## Identity model

- **Registration** creates the user in Keycloak, then triggers Keycloak's native email verification (`executeActionsEmail(["VERIFY_EMAIL"])`) — the verification email is sent by Keycloak via SMTP (MailHog locally), and `emailVerified` is tracked in Keycloak.
- **Login / refresh / logout** proxy Keycloak's token endpoints; logout revokes the refresh token server-side.
- **Roles** come from Keycloak's `realm_access.roles` JWT claim: `customer`, `employee` (organizer), `admin`. See [Roles in `common-security`](shared-modules).

## Background jobs

- **`UnverifiedUserCleanupScheduler`** — runs nightly (02:00) and deletes accounts older than 7 days that never verified their email. ShedLock ensures only one instance runs it.

## Related

- [Keycloak setup & test users](INSTALLATION) · [Security model](https://github.com/CRV96/booking_platform/blob/main/SECURITY.md) · [Application flows](application-flows)
