# Initialization

Contains the complete baseline state for a fresh installation — the merged result of all releases to date.

## Contents

```
init/
  keycloak/
    booking-platform-realm.json   ← full Keycloak realm (all clients, roles, users)
    master-realm.json
  migrations/
    booking-service/
      V1__create_bookings_table.sql
    payment-service/
      V1__create_payments_table.sql
      V2__create_outbox_events_table.sql
      V3__add_retry_columns_to_payments.sql
```

## How Docker uses this

**Keycloak** mounts `init/keycloak/` as its import directory (`--import-realm`).
On first start, Keycloak imports the full realm automatically.

**SQL** migrations are baked into each service's JAR and applied by Flyway on startup.
The files in `init/migrations/` are a manual-use reference — Flyway does not read from here.

## Manual SQL apply (non-Docker / CI setup)

```bash
# booking-service database
psql -U <user> -d bookingdb -f migrations/booking-service/V1__create_bookings_table.sql

# payment-service database
psql -U <user> -d paymentdb -f migrations/payment-service/V1__create_payments_table.sql
psql -U <user> -d paymentdb -f migrations/payment-service/V2__create_outbox_events_table.sql
psql -U <user> -d paymentdb -f migrations/payment-service/V3__add_retry_columns_to_payments.sql
```

## Keeping this folder up to date

When a new release is cut:
1. Copy the new release's SQL file(s) into the matching `migrations/<service>/` folder.
2. If the release adds new Keycloak clients/roles, re-export the full realm from Keycloak
   and replace `keycloak/booking-platform-realm.json`.
