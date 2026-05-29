# OptiSalud-Plus Backend

OptiSalud+ Backend.

## Environment variables

All configuration is injected through environment variables (see
`src/main/resources/application.properties`, `application-dev.properties` and
`application-prod.properties`). The **Default** column shows the value used by the
`dev` profile. In `prod`, database / Redis / JWT / storage credentials have **no
default** and are supplied as Docker file-secrets via the `*_FILE` variants
(e.g. `JWT_SECRET_FILE`); the app fails fast if a required value is missing.

### Core

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile (`dev` / `prod`). The hub maps `BACKEND_PROFILE` to this. |
| `SERVER_PORT` | `8080` | HTTP listen port. |
| `VERSION` | `dev-0.0.1` | App version surfaced by the system-info endpoint (set by the Docker build). |
| `VERSION_DATE` | — | App version date (set by the Docker build). |

### Database (PostgreSQL)

| Variable | Default | Description |
|---|---|---|
| `DATABASE_HOST` | `localhost` | Postgres host (required in `prod`). |
| `DATABASE_PORT` | `5432` | Postgres port. |
| `DATABASE_NAME` | `optisalud` | Database name (required in `prod`). |
| `DATABASE_SSL_MODE` | `disable` | JDBC `sslmode` (required in `prod`). |
| `DATABASE_USER` | `optisalud_app` | Runtime DML role used by the app. |
| `DATABASE_PASSWORD` | `changeme-dev` | Password for the app role (file-secret in `prod`). |
| `DATABASE_MIGRATION_USER` | `postgres` | Flyway migration role (DDL privileges). |
| `DATABASE_MIGRATION_PASSWORD` | `changeme-dev` | Migration role password (file-secret in `prod`). |
| `DATABASE_READONLY_PASSWORD` | `changeme-dev` | Read-only role password, provisioned by `V3__app_roles.sql` (file-secret in `prod`). |

### DB role tuning

Consumed by `V3__app_roles.sql` via Flyway placeholders. They only take effect when
V3 actually runs (fresh bootstrap); changing them on an already-migrated database
does nothing until re-bootstrap or a manual `ALTER ROLE`.

| Variable | Default | Description |
|---|---|---|
| `DATABASE_APP_CONNECTION_LIMIT` | `50` | Max connections for the app role (covers Hikari pool × replicas + headroom). |
| `DATABASE_MIGRATION_CONNECTION_LIMIT` | `5` | Max connections for the migration role. |
| `DATABASE_READONLY_CONNECTION_LIMIT` | `10` | Max connections for the read-only role. |
| `DATABASE_APP_STATEMENT_TIMEOUT` | `30s` | `statement_timeout` for the app role — kills hung queries. |
| `DATABASE_APP_IDLE_IN_TRANSACTION_TIMEOUT` | `60s` | `idle_in_transaction_session_timeout` for the app role. |

### Cache (Redis)

| Variable | Default | Description |
|---|---|---|
| `AUTH_TOKEN_BLACKLIST_PROVIDER` | `redis` | Token blacklist backend: `redis` (HA-ready) or `memory` (single-replica, disables Redis auto-config). |
| `REDIS_HOST` | `localhost` | Redis host. |
| `REDIS_PORT` | `6379` | Redis port. |
| `REDIS_PASSWORD` | — | Redis password (file-secret in `prod`). |

### Mail (SMTP)

| Variable | Default | Description |
|---|---|---|
| `SMTP_HOST` | `localhost` | SMTP server host. |
| `SMTP_PORT` | `587` | SMTP server port. |
| `SMTP_USER` | — | SMTP username. |
| `SMTP_PASSWORD` | — | SMTP password (file-secret in `prod`). |
| `MAIL_FROM` | `noreply@centroopticovicente.com` | Sender of outgoing emails. |
| `MAIL_FROM_NAME` | `OptiSalud Plus` | Sender display name. |
| `MAIL_ADMIN` | `admin@centroopticovicente.com` | Administrative inbox. |

### JWT

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `changeme-…` | HMAC signing secret (file-secret in `prod`, ≥ 32 bytes). |
| `JWT_ISSUER` | `optisalud-plus` | Issuer claim in emitted tokens. |
| `JWT_ACCESS_EXPIRATION_MINUTES` | `15` | Access token TTL (minutes). |
| `JWT_REFRESH_EXPIRATION_DAYS` | `30` | Refresh token TTL (days). |

### CORS

| Variable | Default | Description |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4000` | CSV list of allowed origins. |
| `CORS_ALLOW_LOCALHOST` | `false` | Debug: when `true`, also allows `localhost` / `127.0.0.1` on any port. Keep `false` in production. |

### Errors (RFC 7807)

| Variable | Default | Description |
|---|---|---|
| `PROBLEMS_BASE_URL` | `https://problems-optisalud.centroopticovicente.com` | Base domain for `problem+json` error types. |

### Storage (Cloudflare R2 / S3-compatible)

Opt-in: when `STORAGE_R2_ENABLED=false`, the `S3Client` / `S3Presigner` /
`StorageService` beans are not created, so the app boots without valid storage
credentials. Dev defaults target a local MinIO.

| Variable | Default | Description |
|---|---|---|
| `STORAGE_R2_ENABLED` | `false` | Enable object storage beans and upload endpoints. |
| `STORAGE_R2_ENDPOINT` | `http://localhost:9000` | S3-compatible endpoint (MinIO in dev, R2 in prod). |
| `STORAGE_R2_ACCESS_KEY` | `minioadmin` | Access key (file-secret in `prod`). |
| `STORAGE_R2_SECRET_KEY` | `minioadmin` | Secret key (file-secret in `prod`). |
| `STORAGE_R2_BUCKET` | `optisalud-dev` | Bucket name (`optisalud-prod` in prod). |
| `STORAGE_R2_REGION` | `auto` | Region (Cloudflare R2 uses `auto`). |
