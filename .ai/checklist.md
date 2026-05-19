# Checklist backend (subset del maestro)

> Subset filtrado por tag `[B]` del [checklist maestro](../../centro-optico-vicente/.ai/checklist.md). Mantener sincronizado.
>
> Formato `[P/C]`: Prioridad (P0/P1/P2/P3) + Complejidad (C1-C5). Marcar `- [x]` con fecha al completar.

## FASE 0 — Bootstrap

- [x] [P0/C2] Crear estructura `.ai/` en este repo (CLAUDE, README, checklist subset) — 2026-05-18
- [x] [P1/C1] Instalar skill `spring-boot-best-practices` — `jeffallan/claude-skills@spring-boot-engineer` (5.8K installs) — 2026-05-19
- [x] [P1/C1] Instalar skill `postgresql-expert` — `wshobson/agents@postgresql-table-design` (17.4K installs) — 2026-05-19
- [x] [P1/C1] Instalar skill `redis-expert` — `redis/agent-skills@redis-development` (2.9K installs) — 2026-05-19
- [x] [P1/C1] Instalar skill `owasp-security` — `hoodini/ai-agents-skills@owasp-security` (1.7K installs) — 2026-05-19

## FASE 1 — Bootstrap backend Spring Boot

### Tarea 1.7 — Cimientos infrastructure & config

- [ ] [P0/C2] Agregar Flyway al `build.gradle`: `flyway-core`, `flyway-database-postgresql`
- [ ] [P0/C2] Configurar Flyway en `application.properties`: `spring.flyway.enabled=true`, `locations=classpath:db/migration`, `baseline-on-migrate=true`
- [ ] [P0/C2] Cambiar `spring.jpa.hibernate.ddl-auto=validate` en TODOS los profiles
- [ ] [P0/C3] Crear estructura paquetes: `core/`, `security/`, `common/`, `modules/`
- [ ] [P0/C2] `core.config.JacksonConfig`: ISO 8601, Decimal sin notación científica
- [ ] [P0/C2] `core.config.CorsConfig`: allowedOrigins, métodos, headers, credentials
- [ ] [P0/C3] `core.exception.GlobalExceptionHandler`: RFC 7807 problem+json para 400/401/403/404/409/422/500
- [ ] [P0/C3] `security.SecurityConfig`: filter chain JWT, endpoints públicos
- [ ] [P0/C3] `security.jwt.JwtService`: gen + validación (15min access / 30d refresh)
- [ ] [P0/C2] `security.jwt.JwtAuthenticationFilter` extends OncePerRequestFilter
- [ ] [P0/C2] `security.PasswordEncoder` bean (BCrypt strength 12)
- [ ] [P0/C3] Entidad abstracta `BaseEntity` (id uuid, isActive, status, audit columns)
- [ ] [P0/C2] `core.audit.JpaAuditingConfig` + `AuditorAware` (extrae user del SecurityContext)
- [ ] [P0/C2] `core.config.OpenApiConfig`: Swagger + security scheme JWT, contact info
- [ ] [P0/C2] `core.config.RedisCacheConfig`: serializadores JSON, TTL, prefix
- [ ] [P0/C2] `core.config.S3Config` apuntando a Cloudflare R2 (endpoint custom, path-style)
- [ ] [P0/C3] `common.service.StorageService`: upload, download, generatePresignedUrl, delete, exists
- [ ] [P0/C2] Endpoint `POST /v1/public/contact` (form landing → email + persistencia)
- [ ] [P0/C2] Migration `V1__initial_extensions.sql`: pgcrypto, unaccent, citext
- [ ] [P0/C2] Migration `V2__base_audit_function.sql`: `set_updated_at()` trigger
- [ ] [P0/C2] Migration `V3__contact_messages.sql`
- [ ] [P0/C2] Health checks Actuator: `/actuator/health/liveness`, `/actuator/health/readiness`
- [ ] [P1/C2] Logging estructurado JSON (logback-spring.xml)
- [ ] [P0/C2] `Dockerfile` multi-stage (Gradle build + JRE 25 Alpine runtime)
- [ ] [P0/C2] GitHub Actions: build + test + push imagen Docker Hub `fenixcoreenterprises/optisalud-plus-backend`

### Tarea 1.8 — SMTP

- [ ] [P0/C2] Agregar `spring-boot-starter-mail` al `build.gradle`
- [ ] [P0/C2] Configurar `spring.mail.*` con variables de entorno
- [ ] [P0/C2] `common.service.EmailService`: `sendSimple`, `sendTemplated` (Thymeleaf)
- [ ] [P0/C2] Template `contact-form-received.html`
- [ ] [P1/C2] Cola async con `@Async` + reintentos exponenciales

## FASE 5 — Afiliaciones y Membresías

### Tarea 5.1 — Modelo de datos (migraciones Flyway)

- [ ] [P0/C2] `V10__users_and_roles.sql`: users, roles, permissions, user_roles, role_permissions
- [ ] [P0/C2] `V11__seed_roles.sql`: ADMIN, OPERADOR, ALIADO_USER, AFILIADO_USER, PROMOTOR
- [ ] [P0/C2] `V12__catalogs.sql`: countries, states, cities, genders, document_types, marital_statuses, occupations
- [ ] [P0/C3] `V13__health_catalogs.sql`: medical_specialties, service_categories, ally_types
- [ ] [P0/C3] `V14__allies.sql`: allies, ally_specialties, ally_services, ally_agreements
- [ ] [P0/C3] `V15__ally_users.sql`
- [ ] [P0/C3] `V16__plans.sql`
- [ ] [P0/C2] `V17__seed_plans.sql`: plan personal $10/$5/$5
- [ ] [P0/C3] `V18__members.sql` + member_documents
- [ ] [P0/C3] `V19__beneficiaries.sql`
- [ ] [P0/C3] `V20__medical_records.sql`
- [ ] [P0/C3] `V21__memberships.sql`
- [ ] [P0/C3] `V22__payments.sql`
- [ ] [P0/C3] `V23__benefit_usages.sql`
- [ ] [P0/C3] `V24__promoters.sql`
- [ ] [P0/C3] `V25__commissions.sql`
- [ ] [P0/C3] `V26__referrals.sql`
- [ ] [P0/C2] `V27__notifications.sql`
- [ ] [P0/C2] `V28__digital_cards_view.sql`
- [ ] [P0/C2] Índices clave: members(document), memberships(status, next_due), benefit_usages, payments(status)
- [ ] [P1/C2] Índice GIN full-text en members.full_name con unaccent

### Tarea 5.2 — Auth + Users

- [ ] [P0/C2] Entidades User, Role, Permission, UserRole, RolePermission (JPA)
- [ ] [P0/C2] Repositorios + Services + DTOs + Mappers
- [ ] [P0/C2] `POST /v1/auth/login`
- [ ] [P0/C2] `POST /v1/auth/refresh`
- [ ] [P0/C2] `POST /v1/auth/logout` (Redis blacklist)
- [ ] [P0/C2] `POST /v1/auth/recover-password` + `reset-password`
- [ ] [P0/C2] `GET /v1/me`
- [ ] [P0/C3] `/v1/admin/users` CRUD + RSQL
- [ ] [P1/C2] `POST /v1/me/change-password`
- [ ] [P1/C2] Anti-brute-force (lock IP+identifier)

### Tarea 5.3 — Aliados

- [ ] [P0/C2] Entidades Ally, AllyType, MedicalSpecialty, AllyService, AllyAgreement, AllyUser
- [ ] [P0/C2] Repos + Services (findByDocument, searchByLocationAndSpecialty)
- [ ] [P0/C2] DTOs (Create, Update, ListItem, Detail, Agreement)
- [ ] [P0/C3] `/v1/admin/allies` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/allies/{id}/specialties|services|agreements|users`
- [ ] [P0/C2] `GET /v1/public/allies` (directorio público)
- [ ] [P0/C2] `GET /v1/public/allies/{id}`
- [ ] [P0/C2] Upload logo aliado vía StorageService → R2

### Tarea 5.4 — Afiliados + Familia

- [ ] [P0/C2] Entidades Member, Beneficiary, MemberDocument, MedicalRecord
- [ ] [P0/C3] Validaciones custom: cédula VE (V-/E-), edad min 18, max 3 beneficiarios
- [ ] [P0/C3] `/v1/admin/members` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/members/{id}/beneficiaries` CRUD
- [ ] [P0/C3] `/v1/admin/members/{id}/medical-record` (`@PreAuthorize`)
- [ ] [P0/C2] `POST /v1/admin/members/{id}/upload-document`
- [ ] [P0/C3] `/v1/me/member`
- [ ] [P0/C2] Anonimización en queries de aliados (no expone MedicalRecord)

### Tarea 5.5 — Planes + Membresías

- [ ] [P0/C2] Entidades Plan, Membership
- [ ] [P0/C2] `/v1/admin/plans` CRUD
- [ ] [P0/C2] `POST /v1/admin/members/{id}/memberships`
- [ ] [P0/C2] `PUT /v1/admin/memberships/{id}/cancel|reactivate`
- [ ] [P0/C3] `MembershipStatusService` (SOLVENTE/INACTIVO según pagos+grace)
- [ ] [P0/C3] Job diario `@Scheduled` actualiza status, envía notificación
- [ ] [P1/C2] `GET /v1/admin/memberships/dashboard` KPIs

### Tarea 5.6 — Pagos manuales

- [ ] [P0/C2] Entidad Payment
- [ ] [P0/C3] `POST /v1/admin/payments` (con soporte multipart → R2)
- [ ] [P0/C3] `PUT /v1/admin/payments/{id}/approve`
- [ ] [P0/C2] `PUT /v1/admin/payments/{id}/reject`
- [ ] [P0/C2] `GET /v1/admin/payments` + RSQL
- [ ] [P0/C2] `GET /v1/admin/payments/{id}/support` (presigned URL)
- [ ] [P0/C2] `GET /v1/me/payments`
- [ ] [P0/C2] Templates `payment-received.html` + `payment-approved.html`

### Tarea 5.7 — Promotores + Comisiones + Referidos

- [ ] [P0/C2] Entidades Promoter, Commission, Referral
- [ ] [P0/C3] `/v1/admin/promoters` CRUD
- [ ] [P0/C3] `CommissionService` calcula al aprobar pago inicial
- [ ] [P0/C2] `GET /v1/admin/commissions`
- [ ] [P0/C2] `POST /v1/admin/commissions/payout` cierre ciclo
- [ ] [P0/C2] `GET /v1/promoter/dashboard`
- [ ] [P0/C3] `ReferralService` valida code + aplica descuento
- [ ] [P0/C2] `POST /v1/admin/referral-codes`
- [ ] [P0/C2] `GET /v1/me/referrals`

### Tarea 5.8 — Validador (CRÍTICO p95 < 200ms)

- [ ] [P0/C3] `GET /v1/ally/validate/{document}`
- [ ] [P0/C3] Cache Redis `validator:{document}` TTL 60s + invalidación al cambiar membership status
- [ ] [P0/C2] `POST /v1/ally/benefit-usage`
- [ ] [P0/C2] `GET /v1/ally/usage-history`
- [ ] [P0/C2] Métricas Redis contador validaciones/día
- [ ] [P1/C2] Rate limit por aliado (1000/día default)
- [ ] [P1/C3] `GET /v1/admin/usage-metrics`

### Tarea 5.9 — Notificaciones + Carnet digital

- [ ] [P0/C2] Entidad Notification (cola persistente)
- [ ] [P0/C3] NotificationService: enqueue + envío async + reintentos
- [ ] [P0/C2] Templates payment-reminder/overdue, welcome, suspended, referral
- [ ] [P0/C3] Job diario detecta vencimientos próximos
- [ ] [P0/C3] Job diario detecta vencidos en gracia
- [ ] [P0/C2] `GET /v1/me/digital-card` + QR generado
- [ ] [P0/C2] `GET /v1/me/family` + `/v1/me/usage-history`

### Tarea 5.10 — Optimización 100K

- [ ] [P1/C3] EXPLAIN ANALYZE queries críticas
- [ ] [P1/C2] Índices adicionales según EXPLAIN
- [ ] [P1/C3] Tuning HikariCP
- [ ] [P1/C2] `@Cacheable` catálogos TTL 1h
- [ ] [P1/C3] Paginación obligatoria (default 20, max 100)
- [ ] [P1/C2] Lazy loading + @EntityGraph/JOIN FETCH (evitar N+1)
- [ ] [P2/C3] Vista materializada `member_metrics_mv` refresh 1h
- [ ] [P2/C3] Particionamiento futuro: benefit_usages, payments
- [ ] [P2/C3] Tests k6: 1000 concurrent validaciones, p95 < 200ms

### Tarea 5.11 — Reportes

- [ ] [P1/C3] `GET /v1/admin/reports/memberships|allies|commissions`
- [ ] [P1/C2] `GET /v1/admin/reports/export?type=...&format=csv`

### Tarea 5.12 — Hardening + QA

- [ ] [P0/C3] OWASP Top 10 audit con skill
- [ ] [P0/C2] CSP estricto
- [ ] [P0/C2] Validación uploads (MIME real, tamaño max)
- [ ] [P0/C2] Audit logs acciones sensibles
- [ ] [P1/C3] Tests integrales: 70% coverage services críticos
- [ ] [P1/C3] Tests E2E con Testcontainers

## Notas

- Marcar `[x]` con fecha al completar.
- Sincronizar avance con el [checklist maestro](../../centro-optico-vicente/.ai/checklist.md) tag `[B]`.
- Actualizar [`context/current-state.md`](context/current-state.md) al cierre de cada sesión.
