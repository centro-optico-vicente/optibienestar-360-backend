# Estado actual del backend (snapshot)

> **Actualizado:** 2026-10-24 — reescrito contra `origin/main` (`11d75da`, PR #337). Reemplaza el snapshot de bootstrap del 2026-05-31 y la nota de refresh parcial del 2026-10-08 (cierra el hallazgo H-E-1 de la [auditoría del hub 2026-09-23](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/notes/2026-09-23_audit.md) y el B-19 / recomendación 5 de su anexo de backend).
>
> Qué existe HOY en `optibienestar-360-backend`. Actualizar al cierre de cada sesión productiva. **No duplicar aquí el avance por vertical** — la fuente de verdad son los checkboxes de [`../checklists/`](../checklists/) y el dashboard de [`../checklist.md`](../checklist.md).

## Resumen

| Aspecto | Estado |
|---|---|
| Stack | ✅ Spring Boot 4.0.6 + Java 25 + Gradle 9.4.1, Jetty embebido ([ADR LOC-0001](../decisions/loc-0001-jetty-over-tomcat.md)) |
| Código | ✅ ~850 archivos Java en `src/main/java`; ~97 clases `@RestController` |
| Paquetes raíz | ✅ `core/` (audit, config, display, dto, entity, exception, jpa, util, validation, web) · `security/` (JWT + `PermissionResolver`) · `common/` (email, storage, system-info) · `modules/` |
| Módulos de negocio (`modules/`) | ✅ 26: `affiliation`, `ally`, `auth`, `bank`, `benefit`, `campaign`, `card`, `catalog`, `contact`, `corporate`, `currency`, `document`, `exchangerate`, `member`, `membership`, `notification`, `organization`, `payment`, `person`, `promoter`, `promotion`, `scheduling`, `subsidy`, `system`, `terms`, `validator` |
| Migraciones Flyway | ✅ 187 archivos `V*.sql`, última `V190__affiliation_requests.sql` (hay huecos de numeración, ver `git log` de cada migración). JPA en `validate` en todos los perfiles; nunca editar una migración aplicada |
| Persistencia | ✅ PostgreSQL 15, PK `{tabla}_id BIGINT` + `uuid` público ([ADR LOC-0005](../decisions/loc-0005-table-id-convention.md)), soft-delete ([LOC-0002](../decisions/loc-0002-soft-delete.md)), columnas de auditoría ([LOC-0003](../decisions/loc-0003-audit-columns.md)) |
| Seguridad | ✅ JWT (access + refresh en Redis, `sid`), autorización por **permiso** granular vía `@PreAuthorize(hasAuthority)` — el JWT lleva permisos, no roles ([spec 05](../specs/05-roles-permissions.md)) |
| Contrato JSON | ✅ camelCase + convención `_Display` ([hub ADR 0019](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0019-api-json-casing-contract.md), [hub ADR 0014](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0014-display-value-convention.md)) — ver [`api-conventions.md`](api-conventions.md) |
| Auditoría | ✅ `@Auditable` + 3 tablas de bitácora + `entity_config` ([spec 16](../specs/16-audit.md)) |
| i18n | ✅ Bilingüe `es`/`en` (`messages*.properties`, plantillas de correo `_es`/`_en`) — [spec 14](../specs/14-i18n.md), vertical 11 al 100 % |
| Multi-moneda | ✅ `currencies` / `exchange_rates` / `organizations` ([hub ADR 0015](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0015-multi-currency-exchange-rates.md)) |
| Reportes | ✅ 4 reportes JasperReports (`resources/reports/*.jrxml`, `GenericDocumentController`) + persistencia `attached_files` / `report_audit_log` ([hub ADR 0020](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0020-reporting-engine-reality-amendment.md)). openhtmltopdf + fastexcel también en el classpath ([spec 15](../specs/15-reporting-documents.md)) |
| Jobs programados | ✅ 15 implementaciones de `ScheduledJobRunner` sobre `scheduled_jobs` (módulo `scheduling`, CRUD admin en `AdminScheduledJobController`) |
| Cache | ✅ Spring Cache: `simple` en dev, Redis en prod; Caffeine disponible |
| Storage | ✅ Spring Cloud AWS S3 → Cloudflare R2 (MinIO en dev) ([spec 08](../specs/08-storage-r2.md)) |
| Correo | ✅ `EmailService` + plantillas Thymeleaf bilingües en `resources/templates/` |
| API docs | ✅ springdoc: `/swagger-ui.html` (redirects de variantes `/swagger-ui*` → `/swagger-ui/index.html`), `/v3/api-docs` |
| Tests | ✅ ~117 clases `*Test` (unitarias, JUnit 5 + Mockito) + ~37 `*IT` (integración contra PostgreSQL). **No correr en local** — CI los corre |
| CI/CD | ✅ `.github/workflows/ci.yaml` (`./gradlew build` con servicio `postgres:15-alpine`) + `publish.yaml` (imagen Docker; requiere `target: runtime` + `no-cache`) |
| Docker | ✅ `docker/alpine.Dockerfile` + `docker/debian.Dockerfile` |
| Arranque local | ✅ `start.sh` (perfil dev) — ver [playbook `local-build`](../playbooks/local-build.md) y `GUIA-DESARROLLO-LOCAL.md` |

## Dónde mirar el detalle

| Necesito | Archivo |
|---|---|
| Avance por vertical / qué falta | [`../checklist.md`](../checklist.md) + [`../checklists/`](../checklists/) |
| Esquema de BD | [`../specs/02-database.md`](../specs/02-database.md) + `src/main/resources/db/migration/` |
| Pagos unificados IN/OUT | [`../specs/11-billing-manual.md`](../specs/11-billing-manual.md) |
| Motor de comisiones | [`../specs/12-commissions.md`](../specs/12-commissions.md) |
| Crear entidad / migración / endpoint / rol | [`../playbooks/`](../playbooks/) |

## Cómo se recalculan las cifras de este snapshot

```bash
git ls-tree -r --name-only origin/main src/main/resources/db/migration | grep -c '\.sql$'   # migraciones
git ls-tree -r --name-only origin/main src/main/java | grep -c '\.java$'                   # archivos Java
git grep -l '@RestController' origin/main -- src/main/java | wc -l                          # controllers
git ls-tree -r --name-only origin/main src/test/java | grep -c 'Test\.java$'              # tests unitarios
git ls-tree -r --name-only origin/main src/test/java | grep -c 'IT\.java$'                # tests de integración
```

## Cambios recientes

- **2026-10-21** — Solicitudes de afiliación (V190): la autoafiliación pública deja de crear usuario/afiliado/membresía al instante. `POST /v1/public/affiliation-requests` guarda la solicitud `PENDING` con el hash BCrypt de la contraseña y la evidencia del contrato (texto con los datos de la persona + sha256 + versión + IP/User-Agent); `POST /v1/public/terms/AFILIADO/preview` arma el contrato en vivo. Revisión en `/v1/admin/affiliation-requests` (corregir, aprobar, rechazar con motivo) con bitácora `affiliation_request_logs`; al aprobar, si el texto del contrato con los datos finales es el mismo (sha256) se registra la aceptación `SELF_SERVICE`, si no se le pide al entrar. Correos recibida / aprobada (enlace al login, `app.frontend-url`) / rechazada. Permisos `AFFILIATION_REQUEST_*`. Módulo `modules/affiliation`; se retiró `PublicAffiliationService`.
- **2026-05-31** — Tabla `permission_domains` (10 dominios UI con `code`/`name`/`icon`/`display_order`) consolidada en V5; nuevo permiso `ROLE_PERMISSION_EDIT` consolidado en V6 (asignado solo a SYSTEM); `permissions.domain` (texto) → `domain_id` (FK); entidad `PermissionDomain` + repo + `Permission` refactor a `@ManyToOne`; DTOs `PermissionDto`/`PermissionDomainDto`; `PermissionService.getCatalog()` + `PermissionController GET /v1/admin/permissions` con `@PreAuthorize("hasAuthority('ROLE_PERMISSION_EDIT')")`; ADR 0009 espejado al backend; convenciones de trabajo con IA agregadas a `.ai/CLAUDE.md`.
- **2026-05-28** — Connection limits + statement timeouts por rol DB (V3 hardening, deployment env vars).
- **2026-05-25** — Endpoint `/v1/system-info` con metadata de release.
- **2026-05-18 → 2026-05-24** — Bootstrap completo del módulo auth (login/refresh/logout/recover/me/admin users), módulo catalog (10 catálogos públicos+admin), módulo contact. Migraciones V1–V10. Hardening Postgres (REVOKE PUBLIC, search_path, connection limits).
- **2026-05-18 (inicial)** — Bootstrap Spring Boot 4 + Java 25 + dependencias clave + `.ai/` local.
