# Alcance 10 — Optimización, Reportes y Hardening

> Cierre de Fase 2. Requiere datos reales para EXPLAIN y cobertura de tests.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Optimización

- [ ] [P1/C3] EXPLAIN ANALYZE queries críticas (validate, memberships listing, payments)
- [ ] [P1/C2] Índices adicionales según EXPLAIN
- [ ] [P1/C3] Tuning HikariCP (pool size, timeout, keepalive)
- [ ] [P1/C3] Paginación obligatoria (default 20, max 100) en todos los listados — incluye: `findRecentByUserId` (sin LIMIT actual), `findAllByActiveTrue` en roles/permissions (spring-data-jpa)
- [ ] [P1/C1] `user_sessions_log.jti` — cambiar índice B-tree → HASH (`USING HASH`) para lookups de igualdad pura (jti equality-only) — migración V(N) (postgresql-expert)
- [ ] [P1/C2] `findByPasswordResetToken` en `UserRepository` — cambiar return type a proyección `UserPasswordResetView` (uuid + passwordResetTokenExpiresAt) — evita cargar entidad completa con roles en flujo de reset (spring-data-jpa)
- [ ] [P1/C2] Lazy loading + `@EntityGraph` / JOIN FETCH (evitar N+1) — incluye: `UserRole.role` EAGER→LAZY (postgresql-expert), revisar cualquier listado RSQL que no use EntityGraph
- [ ] [P2/C3] Vista materializada `member_metrics_mv` refresh 1h
- [ ] [P2/C3] Tests k6: 1 000 validaciones concurrentes, p95 < 200ms

## Reportes

- [ ] [P1/C3] `GET /v1/admin/reports/memberships|allies|commissions`
- [ ] [P1/C2] `GET /v1/admin/reports/export?type=...&format=csv`

## Hardening y QA

- [x] [P0/C3] OWASP Top 10 audit con skill — ejecutado en Vertical 1 (owasp-security + api-security + spring-boot-engineer + redis-expert + postgresql-expert) — 2026-05-24
- [ ] [P0/C3] **DB role `optibienestar360_public` (4to rol, post-modelado completo)** — least-privilege específico para el surface público. Cuando el modelado del schema esté congelado (V11..V27 aplicadas), agregar una nueva migración `V(N)__add_public_db_role.sql` que cree el rol y le otorgue acceso de SOLO LECTURA a la lista de tablas que aparecen en endpoints `/v1/public/*`:
    - **Aliados:** `allies` (filtrado por `is_published`), `ally_services` (filtrado por `is_published AND review_status='APPROVED'`), `ally_specialties` (pivote).
    - **Catálogos referenciados por el directorio público:** `ally_types`, `medical_specialties`, `service_categories`, `countries`, `states`, `cities`.
    - **Planes:** `plans` (filtrado por `is_published`) cuando V13 lo haga (vertical-5 ya tiene anotado que debe incluir el flag desde día 1).
    - **Cualquier tabla extra** que pida Centro Óptico Vicente (futuras secciones de la web como testimonios, FAQs, etc.).
    - **NO acceso a:** `users`, `persons`, `members`, `beneficiaries`, `medical_records`, `member_documents`, `payments`, `commissions`, `referrals`, `ally_agreements`, `ally_users`, `ally_service_review_log`, audit tables, ni nada con PII.

    Setup del rol (mismo patrón que V3 para los otros 3):
    - `CREATE ROLE optibienestar360_public WITH LOGIN PASSWORD '${public_db_password}';`
    - `GRANT CONNECT ON DATABASE %I TO optibienestar360_public;`
    - `GRANT USAGE ON SCHEMA app TO optibienestar360_public;`
    - `GRANT SELECT ON <tabla>` por cada tabla habilitada (uno por uno — **NO** `GRANT SELECT ON ALL TABLES`).
    - `ALTER ROLE optibienestar360_public SET search_path = app, public;` (CVE-2018-1058).
    - `ALTER ROLE optibienestar360_public CONNECTION LIMIT ${public_conn_limit};` — más bajo que `app` ya que el tráfico público se cachea (recomendado: 10-20).
    - `ALTER ROLE optibienestar360_public SET statement_timeout = '${public_statement_timeout}';` — más estricto que app (recomendado: 5s; las queries públicas son simples y deben ser rápidas).

    Setup en backend Spring Boot — **defensa en profundidad a nivel de connection pool**:
    - Segundo DataSource `publicDataSource` cableado vía `@Configuration` separada con sus propios HikariCP settings (`spring.public-datasource.*` en `application.properties`).
    - `@Primary` queda en el `optibienestar360_app` DataSource (admin/authenticated routes).
    - `EntityManager` o `JdbcTemplate` dedicados al public pool; inyectarlos solo en los services consumidos por controllers `/v1/public/*` (AlliesService.publicDirectory, AlliesService.publicGetByUuid, futuras public-plans, etc.).
    - **Beneficio**: si un endpoint público tiene SQL injection o un bug que filtra params, el atacante solo puede SELECT de las tablas habilitadas — no puede tocar `users`, `payments`, ni escribir.

    Parametrizar las contraseñas via Flyway placeholder + env vars (`PUBLIC_DB_PASSWORD_FILE` con file-secrets pattern). Documentar el nuevo DataSource en `02-database.md` + `06-rest-api.md`. ADR opcional sobre el patrón "split pool por sensibilidad".

- [ ] [P0/C2] CSP estricto
- [ ] [P0/C2] Validación uploads (MIME real, tamaño máximo)
- [ ] [P0/C2] Audit logs para acciones sensibles (aprobación pagos, acceso a MedicalRecord)
- [ ] [P1/C3] Tests integrales: 70% coverage servicios críticos
- [ ] [P1/C3] Tests E2E con Testcontainers
