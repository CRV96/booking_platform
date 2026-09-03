# config-service

**Spring Cloud Config Server.** Serves every other service its configuration on startup, so properties live in one versioned place instead of being scattered across service jars.

| Property | Value |
|----------|-------|
| HTTP port | 8888 |
| gRPC | none |
| Store | none (reads the `config/` folder) |
| Depends on | nothing — **starts first** |

## What it does

On boot, every config-client service calls config-service with its application name and active profile, and receives the matching `config/<profile>/<service>.properties` file. This is why config-service must be the first service up.

- **Backend:** the native filesystem backend, pointed at the repository's `config/` directory. `config/dev/` holds development properties, `config/prod/` holds production.
- **Placeholder pass-through:** properties use `${ENV_VAR:default}` syntax. The server hands the placeholder to the client unchanged, and the client resolves it from **its own** environment. This is how the same config file drives local, Docker, and Kubernetes runs — each supplies different env values.
- **No secrets in git:** credentials and keys are always placeholders resolved from a gitignored `.env` (local) or Kubernetes secrets, never literals in `config/`.

## Configuration model

See **[Configuration](configuration)** for the full explanation of the config repository, the placeholder pattern, and the critical `spring.config.import` requirement (a service that cannot reach config-service silently falls back to whatever local properties it has, which is a common source of "works on my machine" bugs).

## Related

- [eureka-service](eureka-service) — the other platform service; starts second.
- [Installation → Config Server](INSTALLATION) — operational detail and Docker-vs-local behaviour.
