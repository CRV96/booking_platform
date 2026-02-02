# System Initialization

Follow these steps after cloning the repository.

## 1. Start Infrastructure

```bash
docker-compose -f infrastructure/docker/docker-compose.startup.yaml up -d
```

Wait for all services to be healthy (especially Keycloak, which imports the realm on first start).

## 2. Configure Config Server

Update `services/config-service/src/main/resources/application.properties`:

```properties
spring.cloud.config.server.native.search-locations=${CONFIG_SERVER_CONFIGURATIONS_PATH}/{profile}
```

Replace `${CONFIG_SERVER_CONFIGURATIONS_PATH}` with the absolute path to the `config` folder.

Example:
```properties
spring.cloud.config.server.native.search-locations=file:/Users/yourname/booking-platform/config/{profile}
```

### Config Folder Structure

```
config/
├── dev/                    # Development properties
│   ├── user-service.properties
│   ├── event-service.properties
│   └── ...
└── prod/                   # Production properties
    ├── user-service.properties
    ├── event-service.properties
    └── ...
```

Services fetch their config based on their active profile:
- `spring.profiles.active=dev` → fetches from `config/dev/`
- `spring.profiles.active=prod` → fetches from `config/prod/`

## 3. Keycloak Client Secret

The `user-service` needs a client secret to communicate with Keycloak's Admin API.

**Default secret:** `user-service-secret` (configured in `booking-platform-realm.json`)

If you need to regenerate the secret:
1. Open Keycloak Admin Console: http://localhost:8180
2. Login with `admin` / `admin`
3. Select realm: `booking-platform`
4. Go to: **Clients** → **user-service-admin** → **Credentials** tab
5. Click **Regenerate** and copy the new secret

## 4. Set Environment Variables

Export the required environment variables before running services:

```bash
# Required for user-service
export USER_SERVICE_KEYCLOAK_CLIENT_SECRET=<paste-secret-here>

# PostgreSQL credentials (default: admin/admin)
export DB_POSTGRES_USERNAME=admin
export DB_POSTGRES_PASSWORD=admin
```

Or add them to your `.env` file if using the `run-service.sh` script.

### PostgreSQL Credentials

By default, PostgreSQL uses `admin` / `admin` as credentials. These are configured in two places:

1. **Docker Compose** (infrastructure setup):
   `infrastructure/docker/docker-compose.startup.yaml` - defines the database credentials when the container starts

2. **Service properties** (application config):
   `config/dev/*.properties` - services use `${DB_POSTGRES_USERNAME}` and `${DB_POSTGRES_PASSWORD}` environment variables

To change the credentials:
1. Update the Docker Compose file with your new credentials
2. Set the corresponding environment variables before starting services

### Production Environment Variables

For production, these additional variables are needed:

```bash
export POSTGRES_HOST=your-postgres-host
export DB_POSTGRES_USERNAME=your-user
export DB_POSTGRES_PASSWORD=your-password
export KEYCLOAK_URL=https://your-keycloak-url
export EUREKA_URL=http://your-eureka-url/eureka/
export ZIPKIN_URL=http://your-zipkin-url
```

## 5. Start Services

Start in this order:

```bash
# 1. Config Server
./run-service.sh config-service

# 2. Eureka (Service Discovery)
./run-service.sh eureka-service

# 3. Other services (any order)
./run-service.sh user-service
./run-service.sh event-service
# ... etc
```

By default, services run with the `dev` profile. To run with production config:

```bash
SPRING_PROFILES_ACTIVE=prod ./run-service.sh user-service
```

## 6. SonarQube (Optional)

For code quality analysis:

1. Open SonarQube: http://localhost:9000
2. Login with `admin` / `admin` (change password on first login)
3. Generate a token: **My Account** → **Security** → **Generate Tokens**
4. Run analysis:

```bash
./infrastructure/sonarqube/run-sonar.sh <your-token>
```

## Test Users

| Username | Password | Role | Group |
|----------|----------|------|-------|
| admin | admin123 | employee | employees |
| john.doe | customer123 | customer | customers |
| jane.smith | customer123 | customer | customers |
| carlos.garcia | customer123 | customer | customers |

## OAuth2 Token (Postman)

```
POST http://localhost:8180/realms/booking-platform/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=booking-app&username=john.doe&password=customer123
```
