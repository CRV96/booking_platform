# Releases

The `release/` folder is a small, versioned upgrade system for the **non-Flyway** changes a deployment needs — chiefly Keycloak realm updates (new clients, SMTP, themes). SQL schema changes are **not** here; Flyway applies those automatically on service startup. This split keeps each kind of change with the tool that owns it.

## What the release system does — and doesn't

| Change type | Handled by | Where |
|-------------|-----------|-------|
| SQL schema/data | **Flyway** (on service boot) | `services/<service>/src/main/resources/db/migration/` |
| Keycloak realm (clients, SMTP, theme, roles) | **release-manager** | `release/<version>/keycloak/` |
| Fresh-install realm baseline | Keycloak `--import-realm` | `infrastructure/keycloak/realm/booking-platform-realm.json` |

## Layout

```
release/
├── release.yaml                 # current-version + target-version
├── scripts/release-manager.sh   # applies Keycloak changes between the two
├── v2026.09.01/                 # the current baseline (empty = nothing to apply)
└── archive/                     # historical releases, kept for reference
    ├── v1.0.0/  (migrations/)
    ├── v1.0.1/  (keycloak/ migrations/)
    └── v1.0.2/  (keycloak/ — SMTP + email theme; each has a README)
```

Each versioned folder may contain a `keycloak/` directory with JSON files and a `README.md` describing the change (see `archive/v1.0.2/README.md` for a worked example — the email-verification release).

## `release.yaml`

```yaml
current-version: v2026.09.01   # what this environment has applied (managed automatically)
target-version:  v2026.09.01   # what you want it to reach
```

- **Fresh install** → set `current-version: none`. On first run the manager records the target and exits, because `--import-realm` + Flyway already did the full setup.
- **Upgrade** → set `target-version` to the desired version. The manager applies every version **between current (exclusive) and target (inclusive)**, in order — e.g. `current=v1.0.0, target=v1.0.2` applies `v1.0.1` then `v1.0.2`. It updates `current-version` on success. Don't edit `current-version` by hand after the first run.

## How `release-manager.sh` applies a version

For each version in range it looks at that version's `keycloak/*.json` and applies it against the running Keycloak (after waiting for it to be healthy and obtaining an admin token):

- **`realm-*.json`** → applied as a realm-level update (`PUT /admin/realms/booking-platform`) — used for settings like SMTP and email theme.
- **Any other `*.json`** → applied via `partialImport` (`POST .../partialImport`) — used to add clients, roles, etc.

## Running it

The manager runs automatically as the **`release-manager` init container** in the Docker Compose stack (`docker-compose.startup.yaml`) — bring the stack up and versioned Keycloak changes are applied for you. It reads `KC_ADMIN_USER` / `KC_ADMIN_PASSWORD` from the environment.

To apply an upgrade to an existing environment: set `target-version` in `release.yaml`, then restart the stack (or the init container). SQL migrations ride along on the next service startup via Flyway.

## Cutting a new release

1. Create `release/<new-version>/` (date-based, e.g. `v2026.09.01`).
2. Put any Keycloak JSON under `release/<new-version>/keycloak/` and a `README.md` describing the change.
3. Put any SQL under the owning service's `db/migration/` as the next `V<n>__*.sql` (Flyway).
4. Set `target-version: <new-version>` in `release.yaml`.
5. Bring the stack up — the init container applies Keycloak changes; Flyway applies SQL.

## Related

- [Installation → Keycloak](INSTALLATION) · [user-service](user-service) · [Docker deployment](docker-deployment) · [CI/CD](ci-cd)
