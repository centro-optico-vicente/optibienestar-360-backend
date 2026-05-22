# Checklist — FASE 1: Bootstrap backend Spring Boot

> Formato `[P/C]`: Prioridad (P0/P1/P2/P3) + Complejidad (C1-C5). Marcar `- [x]` con fecha al completar.
> Índice: [../checklist.md](../checklist.md)

## Tarea 1.7 — Cimientos infrastructure & config

- [x] [P0/C2] Agregar Flyway al `build.gradle`: `flyway-core`, `flyway-database-postgresql` — 2026-05-19
- [x] [P0/C2] Configurar Flyway en `application.properties`: `enabled`, `locations`, `validate-on-migrate` (base compartida; `baseline-on-migrate` diferenciado por profile) — 2026-05-20
- [x] [P0/C2] Cambiar `spring.jpa.hibernate.ddl-auto=validate` en TODOS los profiles — 2026-05-19
- [x] [P0/C3] Crear estructura paquetes: `core/`, `security/`, `common/`, `modules/` — 2026-05-20
- [x] [P0/C2] `core.config.JacksonConfig`: ISO 8601, Decimal sin notación científica — 2026-05-20
- [x] [P0/C2] `core.config.CorsConfig`: allowedOrigins, métodos, headers, credentials — 2026-05-20
- [x] [P0/C3] `core.exception.GlobalExceptionHandler`: RFC 7807 problem+json para 400/401/403/404/409/422/500 — 2026-05-20
- [x] [P0/C3] `security.SecurityConfig`: filter chain JWT, endpoints públicos — 2026-05-20
- [x] [P0/C3] `security.jwt.JwtService`: gen + validación (15min access / 30d refresh) — 2026-05-20
- [x] [P0/C2] `security.jwt.JwtAuthenticationFilter` extends OncePerRequestFilter — 2026-05-20
- [x] [P0/C2] `security.PasswordEncoder` bean (BCrypt strength 12) — 2026-05-20
- [x] [P0/C3] Entidad abstracta `BaseEntity` (BIGINT PK + UUID externo + audit columns) — 2026-05-20
- [x] [P0/C2] `core.audit.JpaAuditingConfig` + `AuditorAware<UUID>` (extrae user del SecurityContext) — 2026-05-20
- [x] [P0/C2] `core.config.OpenApiConfig`: Swagger + security scheme JWT, contact info — 2026-05-20
- [x] [P0/C2] `core.config.RedisCacheConfig`: serializadores JSON, TTL, prefix — 2026-05-20
- [x] [P0/C2] `core.config.S3Config` apuntando a Cloudflare R2 (endpoint custom, path-style) — 2026-05-20
- [x] [P0/C3] `common.service.StorageService`: upload, download, generatePresignedUrl, delete, exists — 2026-05-20
- [x] [P0/C2] Endpoint `POST /v1/public/contact` (form landing → email + persistencia) — 2026-05-21
- [x] [P0/C2] Migration `V1__initial_extensions.sql`: pgcrypto, unaccent, citext — 2026-05-21
- [x] [P0/C2] Migration `V2__base_audit_function.sql`: `set_updated_at()` trigger — 2026-05-21
- [x] [P0/C2] Migration `V3__contact_messages.sql` — 2026-05-21
- [x] [P0/C2] Health checks Actuator: `/actuator/health/liveness`, `/actuator/health/readiness` — 2026-05-21
- [x] [P1/C2] Logging estructurado JSON (logback-spring.xml) — 2026-05-21
- [x] [P0/C2] `docker/alpine.Dockerfile` + `docker/debian.Dockerfile` multi-stage (JDK 25 builder + JRE 25 runtime, `--mount=type=cache` Gradle) — 2026-05-19
- [x] [P0/C2] `.github/workflows/publish.yaml`: build + test + push Docker Hub `fenixcoreenterprises/optisalud-plus-backend` (Alpine + Debian, prerelease-aware tags) — 2026-05-19
- [x] [P0/C2] `.github/workflows/ci.yaml`: CI independiente (push/PR a main), cancela runs en paralelo — 2026-05-19
- [x] [P1/C2] Cache Gradle en CI vía `actions/setup-java cache: gradle` (`~/.gradle/caches` + `~/.gradle/wrapper`) — 2026-05-20
- [x] [P1/C2] Cache Docker en CI: `docker-build-check` usa JAR precompilado (`from-prebuilt` target) en lugar de re-compilar con Gradle — 2026-05-20

## Tarea 1.8 — SMTP

- [x] [P0/C2] Agregar `spring-boot-starter-mail` al `build.gradle` — 2026-05-19
- [x] [P0/C2] Configurar `spring.mail.*` con variables de entorno — 2026-05-19
- [x] [P0/C2] `common.service.EmailService`: `sendSimple`, `sendTemplated` (Thymeleaf) — 2026-05-21
- [x] [P0/C2] Template `contact-form-received.html` — 2026-05-21
- [ ] [P1/C2] Cola async con `@Async` + reintentos exponenciales
