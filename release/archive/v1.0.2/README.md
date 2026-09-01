# v1.0.2 Upgrade Guide

This release adds email verification on user registration, delegated entirely to Keycloak,
with a custom branded email theme.

---

## What changed

### Email verification flow (Keycloak-native)

When a user registers, Keycloak now sends a verification email automatically via MailHog.
Clicking the link in the email marks `emailVerified = true` directly in Keycloak —
no custom token storage or additional services required.

**Services simplified:**
- `user-service` — removed Redis verification token logic and Kafka publisher; now calls `executeActionsEmail(["VERIFY_EMAIL"])` on the Keycloak Admin API after user creation
- `notification-service` — removed `UserRegisteredEvent` consumer and custom email template
- `graphql-gateway` — removed `verifyEmail` GraphQL mutation and REST `/verify-email` endpoint

**Unverified account cleanup scheduler** remains active in `user-service` (nightly at 02:00,
deletes accounts older than 7 days with `emailVerified = false`).

---

## Step 1 — Keycloak: configure SMTP and email theme

The release-manager applies two realm-level updates automatically:

**`keycloak/realm-smtp.json`** — configures MailHog as the SMTP server:

| Setting | Value |
|---------|-------|
| Host | `bkg-mailhog` |
| Port | `1025` |
| From | `noreply@booking-platform.com` |
| Auth | none |

View outgoing emails at **http://localhost:8025**.

**`keycloak/realm-email-theme.json`** — activates the custom branded email theme (`booking-platform`).
Template files live in `infrastructure/keycloak/themes/booking-platform/email/`.
To modify the email design, edit the FreeMarker templates there and restart Keycloak — no rebuild needed.

> **Fresh install:** Both `smtpServer` and `emailTheme` are already set in
> `init/keycloak/booking-platform-realm.json`, so no manual step is needed.

---

## Changes summary

| Area | Change |
|------|--------|
| Keycloak realm | SMTP server configured to use MailHog; `emailTheme` set to `booking-platform` |
| `infrastructure/keycloak/themes/` | Custom branded HTML + plain-text email verification templates |
| `docker-compose.startup.yaml` | Themes directory mounted into Keycloak container |
| `user-service` | Replaced Redis token + Kafka publish with `executeActionsEmail` |
| `user-service` | Removed `common-events` and `spring-kafka` dependencies |
| `notification-service` | Removed `UserRegisteredEvent` consumer, `email-verification.html` template, and DLT |
| `graphql-gateway` | Removed `verifyEmail` mutation, `EmailVerificationController`, and gRPC `VerifyEmail` RPC |
| `common-proto` | Removed `VerifyEmail` RPC, `VerifyEmailRequest`, `VerifyEmailResponse` |
| `common-events` | Removed `user_events.proto` and `KafkaTopics.USER_REGISTERED` |
