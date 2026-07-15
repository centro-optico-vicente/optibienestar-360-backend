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

> Diseño congelado en [ADR 0012](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0012-reporting-documents-engine.md) · spec técnica: [`../specs/15-reporting-documents.md`](../specs/15-reporting-documents.md).
> Motor único para dos familias: **analítica** (listados/KPIs → XLSX/CSV) y **transaccional** (recibo/cobro/planilla/carnet → PDF/ticket). Agregar un reporte = un bean provider (+ plantilla), sin DDL.

### Motor

- [ ] [P1/C2] Dependencias nuevas: `openhtmltopdf` (+pdfbox), `fastexcel`, `commons-csv` + TTF Unicode (DejaVu/Noto) en `resources/fonts/` — sin ella los acentos y `Bs.` salen rotos. Todas OSS ([ADR 0004](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0004-only-free-tools.md))
- [ ] [P1/C3] `V31__generated_documents.sql` — tabla (`document_type`, `category`, `format`, `storage_key`, `status`, `params JSONB`, `source_module`+`source_entity_uuid`, `trigger_source`, `generated_by`, `expires_at`, `error_message`) + CHECKs de coherencia (`READY ⇒ storage_key`, `FAILED ⇒ error_message`) + 5 índices parciales + **seed del dominio de permisos `DOCUMENTS`** (`DOCUMENT_VIEW_ALL`/`VIEW_OWN`/`GENERATE`, patrón V22/V28). Molde: `V28__notifications.sql`
- [ ] [P1/C3] Interfaces: `ReportDataProvider` (`code`+`fetch`), `DocumentRenderer` (`format`+`render`), `DocumentModel` sealed (`TabularModel` | `TemplateModel`), `DocumentService` orquestador
- [ ] [P1/C2] Registros indexados en `@PostConstruct` con fail-fast ante `code()`/`format()` duplicados (copiar `JobExecutionService.buildRunnerIndex`); validar `format ∈ supportedFormats` antes de renderizar (422 limpio)
- [ ] [P1/C3] Renderers: `PdfRenderer` (Thymeleaf→HTML→jsoup→`PdfRendererBuilder`), `XlsxRenderer` (fastexcel streaming), `CsvRenderer` (commons-csv, BOM UTF-8 para Excel-VE), `TicketPdfRenderer` (mismo PDF con perfil CSS 58/80 mm)
- [ ] [P2/C3] `TICKET_ESCPOS` — **diferido**: entra tras la misma interfaz `DocumentRenderer` sin tocar el motor, cuando haya térmica POS real. Requiere agente local/WebUSB + dialecto por fabricante (Epson/Star)

### Entrega

- [ ] [P1/C2] Storage: key `reports/{yyyy}/{MM}/{documentType}/{uuid}-{safeName}.{ext}` (reusar `PaymentsService.safeName`) + `ObjectProvider<StorageService>` (422 limpio con R2 off) + reuso de `clampTtl` + lifecycle rule del prefijo `reports/` en R2 (90d analíticos / sin borrado automático para legales)
- [ ] [P1/C2] `EmailService.sendTemplatedWithAttachment(...)` — el `MimeMessageHelper(msg, true, "UTF-8")` **ya es multipart**, solo falta `addAttachment(fileName, ByteArrayResource, contentType)`
- [ ] [P1/C2] Modo `EMAIL_LINK`: el correo enlaza al endpoint autenticado `GET /v1/documents/{uuid}/download` (presigned fresca por llamada), **no** una URL firmada larga — el presign de R2 topa a 7 días y "acceder después" debe sobrevivirlo
- [ ] [P1/C3] Async híbrido *sync-then-202* (copiar `max_sync_seconds` de `JobExecutionService`) sobre un `reportExecutor` dedicado; el SPA hace polling hasta `READY`

### Endpoints

- [ ] [P1/C3] `GET /v1/admin/reports/memberships|allies|commissions` + `GET /v1/admin/dashboard` (KPIs JSON, `REPORT_VIEW_DASHBOARD`)
- [ ] [P1/C2] `GET /v1/admin/reports/export?type=...&format=csv|xlsx` (`REPORT_EXPORT`) — el `format=csv` reservado **se absorbe** como el CSV renderer del motor: se extiende el enum, no se duplica el contrato
- [ ] [P1/C3] `POST /v1/documents` (200 o 202) · `GET /v1/documents` (RSQL) · `GET /v1/documents/{uuid}` (polling) · `GET /v1/documents/{uuid}/download?ttlMinutes=` · `POST /v1/documents/{uuid}/email`
- [ ] [P2/C2] Azúcar por entidad: `POST /v1/admin/payments/{uuid}/receipt?format=pdf|ticket&delivery=...`
- [ ] [P1/C2] **Auth efectiva = permiso de documento ∧ permiso de la entidad origen** (un recibo exige además `PAYMENT_VIEW_ALL`/`PAYMENT_VIEW_OWN`; el AFILIADO usa `DOCUMENT_VIEW_OWN` y **nunca** recibe `REPORT_EXPORT`)

### Documentos del negocio

- [ ] [P1/C3] `PAYMENT_RECEIPT` (PDF + `TICKET_PDF`) — provider + plantillas `_es`/`_en` + hook en `PaymentsService.approve` + reemisión manual. Entrega: adjunto + persistido
- [ ] [P1/C2] `CHARGE_NOTICE` (cobro, PDF + ticket) — manual o evento de vencimiento
- [ ] [P1/C3] `ACCOUNTS_PAYABLE` (XLSX + PDF resumen) = **comisiones a promotores**: el provider reusa el agrupamiento de `CommissionPayoutService` (comisiones `PENDING` por promotor/ciclo) + `AccountsPayableReportJobRunner implements ScheduledJobRunner` + fila semilla en `scheduled_jobs` (cron mensual, `America/Caracas`) → email con **enlace** a finanzas. No hay módulo de payables de proveedores
- [ ] [P1/C2] `MEMBER_REGISTRATION_FORM` (planilla, PDF) — evento de alta de afiliado + manual; persistido (auditoría)
- [ ] [P2/C2] Listados analíticos: `MEMBERS_LISTING`, `ALLIES_USAGE`, `COMMISSIONS_BY_PROMOTER` (XLSX/CSV)
- [ ] [P2/C1] Migrar el correo transaccional a la cola persistente `notifications` cuando aterrice `NotificationService` ([vertical-9](vertical-9-notificaciones-y-carnet.md)) — el motor **no** debe re-implementar reintentos/backoff

> El carnet (`MEMBER_CARD`, PDF) ya vive en [vertical-9](vertical-9-notificaciones-y-carnet.md); al implementarlo, generarlo con este motor en vez de una ruta aparte.

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
