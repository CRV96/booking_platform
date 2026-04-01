# v1.0.1 Upgrade Guide

This release adds two things on top of v1.0.0:

1. **Keycloak client** for `notification-service` → `user-service` gRPC communication
2. **Payment retry columns** — schema change that enables the `PENDING_RETRY` scheduler

---

## Step 1 — Keycloak: import notification-service client

Go to Keycloak → your realm → **Action** dropdown → **Partial Import**, upload
`keycloak/notification-service-client.json`, select **Skip** or **Overwrite** for
duplicates, then click **Import**.

This creates the `notification-service` client and its service account with the
`view-users` role, which allows notification-service to look up recipient emails
from user-service over gRPC.

---

## Step 2 — Database migration: add retry columns to payments

Flyway applies this automatically when payment-service starts. If you need to run
it manually against an existing `paymentdb`:

```bash
psql -U <user> -d paymentdb -f migrations/payment-service/V3__add_retry_columns_to_payments.sql
```

**What this adds to the `payments` table:**

| Column | Type | Description |
|--------|------|-------------|
| `retry_count` | `INT NOT NULL DEFAULT 0` | Number of retry attempts made so far |
| `max_retries` | `INT NOT NULL DEFAULT 3` | Maximum attempts before transitioning to FAILED |
| `next_retry_at` | `TIMESTAMPTZ NULL` | When the next retry should run (exponential backoff) |

A partial index `idx_payments_pending_retry` is also created on `next_retry_at`
filtered to `status = 'PENDING_RETRY'`, keeping the scheduler's due-payment query fast.

### Backoff behaviour

With the default config (`base=60s`, `multiplier=2`, `max=3`):

| Attempt | Delay before retry |
|---------|--------------------|
| 1st | 60 s |
| 2nd | 120 s |
| 3rd | 240 s → FAILED |

These are tunable in `config/dev/payment-service.properties`:

```properties
payment.retry.max-attempts=3
payment.retry.backoff-base-seconds=60
payment.retry.backoff-multiplier=2
payment.retry.scheduler.interval=60000
```

---

## Changes summary

| Area | Change |
|------|--------|
| Keycloak | New `notification-service` client with `view-users` service account role |
| `payments` table | 3 new columns + 1 partial index for retry scheduling |
| payment-service | `PaymentRetryScheduler` — polls and retries `PENDING_RETRY` payments |
| notification-service | `onEventCancelled` / `onEventUpdated` email flows via booking-service gRPC |
