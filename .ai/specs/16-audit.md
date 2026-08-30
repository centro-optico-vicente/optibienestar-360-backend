# 16 — Auditoría (login, cambios de datos, reportes)

> Relacionado: [`04-security.md`](04-security.md) (JWT, sesiones), [`05-roles-permissions.md`](05-roles-permissions.md) (permisos granulares), [`07-cache.md`](07-cache.md) (cache de config), [`15-reporting-documents.md`](15-reporting-documents.md) (motor de reportes).

## Contexto

El backend hoy solo tiene auditoría de fila estándar (`createdAt/updatedAt/createdBy/updatedBy` vía `BaseAuditEntity` + `AuditingEntityListener`) y bitácoras ad-hoc por dominio (`subsidy_audit_log`, `member_promoter_assignments`). No existe:
- Registro de intentos de login fallidos (solo un contador `failedLoginAttempts` en `User`, sin historial con IP/user-agent).
- Log de qué reportes generó quién y con qué parámetros.
- Log centralizado de create/update/delete sobre las ~27 entidades de negocio (cada `*Service` hace su propio CRUD manual, sin AOP).

Objetivo: cerrar esas 3 brechas con trazabilidad completa (quién, cuándo, desde dónde, qué cambió), sin reescribir los servicios existentes, y con auditoría de datos configurable por entidad sin redeploy.

Se usó como referencia el patrón legado de `proyecto-iv-mh` (`tseg_BITACORA_ACCESO` / `tseg_BITACORA_CAMBIO` / `tseg_REPORTE`, con FK de cambio→acceso y reporte→acceso) para el diseño relacional entre las 3 tablas.

## Decisiones

1. **Captura de cambios de datos vía AOP**: `@Aspect` + anotación `@Auditable` sobre create/update/delete de cada `*Service`. `spring-boot-starter-aspectj` ya está en `build.gradle:27`.
2. **Alcance configurable por entidad**: tabla `audit_entity_config` (toggle por entidad y por acción), cacheada, editable sin redeploy.
3. **3 tablas separadas** (`login_audit_log`, `data_change_audit_log`, `report_audit_log`) con FK opcional hacia `login_audit_log` (nullable — jobs/sistema no tienen sesión HTTP). `login_audit_log` también funciona como registro de sesión (rol, idioma, IP, user-agent, hostname); su `uuid` se propaga como claim `sid` en el JWT.
4. **Reportes**: metadata + referencia al archivo generado. Requiere que `GenericDocumentController` empiece a persistir en R2/`attached_files` (hoy solo devuelve bytes).
5. **Ciclo de vida de sesión**: se registra cierre/expiración y se invalida el reuso de un `sid` cerrado/expirado (capa adicional sobre el blacklist de `jti` existente), con un chequeo simple de un solo booleano (`is_valid`) en el hot path.
6. **Fail-safe, nunca bloqueante**: si `audit_entity_config` no tiene fila para una entidad, la operación de negocio continúa igual (solo warning en logs).
7. **Permisos granulares por dominio**: `<DOMAIN>_AUDIT_VIEW` / `<DOMAIN>_AUDIT_RESTORE` / `<DOMAIN>_REPORT_GENERATE` + `REPORT_SHARE` único, siguiendo el patrón de `V54__document_permissions.sql`. Restaurar y compartir quedan con permiso y esquema preparados pero **sin implementar** en este entregable.
8. **Override global sobre `audit_entity_config`**: en el singleton `system_configs` (V67), dos columnas de 3 valores — `data_change_audit_mode` / `report_audit_mode` ∈ {`PER_ENTITY` (default, respeta `audit_entity_config` fila por fila), `FORCE_ENABLED` (audita todo sin importar `audit_entity_config`), `FORCE_DISABLED` (kill switch: no audita nada, sin importar `audit_entity_config`)} — más un interruptor simple `login_audit_enabled` (default `TRUE`) para `login_audit_log`, que no tiene granularidad por entidad y por eso no necesita el modo de 3 valores. El futuro `DataChangeAuditAspect`/`ReportAuditService` deben resolver primero este override global y solo consultar `audit_entity_config` cuando el modo sea `PER_ENTITY`; mismo criterio fail-safe (si no se puede resolver, se asume `PER_ENTITY`/`TRUE` y nunca bloquea la operación de negocio). Apagar `login_audit_enabled` es delicado: hoy `login_audit_log` también es el origen del claim `sid` del JWT (ver Decisión 3) — la implementación del hook de login deberá definir explícitamente qué pasa con `sid`/`is_valid` cuando está en `false` (pregunta abierta para cuando se implemente `AuthService.login()`, no bloquea el DDL).

## Migraciones Flyway (V60 en adelante; última existente V59)

- **V60__audit_entity_config.sql** — config por entidad: `entity_key`, `display_name`, `table_name` (opcional), `enabled`, `audit_create/update/delete/report`, `capture_before_after`, auditoría estándar. Seed con las ~27 entidades de escritura conocidas (incluye `subsidy`, que coexiste con `subsidy_audit_log` — ver nota abajo).
- **V61__login_audit_log.sql** — todos los intentos de login (éxito y fallo) + ciclo de vida de sesión.
- **V62__data_change_audit_log.sql** — create/update/delete genérico, before/after JSONB.
- **V63__report_audit_log.sql** — generación de reportes + referencia a archivo.
- **V64__audit_permissions.sql** — `AUDIT_VIEW_LOGIN`, `AUDIT_MANAGE_CONFIG`.
- **V65__report_share.sql** — tabla preparada para compartir reportes (sin funcionalidad aún).
- **V66__audit_granular_permissions.sql** — permisos por dominio (§7).
- **V68__system_configs_audit_overrides.sql** — override global sobre `system_configs` (V67): `data_change_audit_mode`, `report_audit_mode`, `login_audit_enabled` (ver Decisión 8).
- **V69__audit_view_all_permission.sql** — `AUDIT_VIEW_ALL` (cambios de datos, cross-entity).
- **V70__report_audit_view_all_permission.sql** — `REPORT_AUDIT_VIEW_ALL` (reportes, cross-entity) — mismo patrón que V69.
- **V71__report_audit_granular_permissions.sql** — `<DOMAIN>_REPORT_AUDIT_VIEW` por dominio (espejo de `<DOMAIN>_AUDIT_VIEW` de V66, pero para `report_audit_log` en vez de `data_change_audit_log`; distinto de `<DOMAIN>_REPORT_GENERATE`, que es permiso para *generar*, no para *ver el historial*). `REPORT_AUDIT_VIEW_ALL` (V70) sigue siendo el override cross-entity.
- **V72__system_configs_login_session_expiration.sql** — `login_session_expiration_days` (default 30, `CHECK > 0`) en `system_configs` — cuántos días dura activa una sesión de `login_audit_log` antes de que `LoginSessionSweepJob` la expire, configurable sin redeploy e independiente de `jwt.refresh-expiration-days` (TTL del JWT en sí).

Todas con `SET search_path TO app, public;` (convención de `V41__subsidies.sql`).

### DDL

```sql
-- V60__audit_entity_config.sql
SET search_path TO app, public;

CREATE TABLE audit_entity_config
(
    audit_entity_config_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    entity_key              VARCHAR(80)  NOT NULL UNIQUE,   -- ej. "ally" — debe matchear @Auditable(entity=...)
    display_name            VARCHAR(120) NOT NULL,
    table_name               VARCHAR(120),                   -- opcional, informativo (nombre físico de tabla)
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_create            BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_update            BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_delete            BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_report            BOOLEAN      NOT NULL DEFAULT TRUE,
    capture_before_after    BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                   TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID
);

CREATE INDEX idx_audit_entity_config_key ON audit_entity_config (entity_key);

INSERT INTO audit_entity_config (entity_key, display_name) VALUES
    ('ally', 'Aliados'),
    ('member', 'Afiliados'),
    ('plan', 'Planes'),
    ('promoter', 'Promotores'),
    ('role', 'Roles'),
    ('user', 'Usuarios'),
    ('subsidy', 'Subsidios');
    -- ... resto de catálogos y entidades de negocio
```

`capture_before_after=false` → solo se registra el hecho (actor/acción/entidad/timestamp), sin `before_json`/`after_json` (útil para PII o campos voluminosos).

`subsidy` no se excluye: `subsidy_audit_log` es una bitácora de negocio hecha a mano que no necesariamente conserva registros borrados; `data_change_audit_log` sí (incluye DELETE con `before_json` completo), pensando en una futura herramienta de restauración — ambos logs coexisten intencionalmente.

```sql
-- V61__login_audit_log.sql
SET search_path TO app, public;

CREATE TABLE login_audit_log
(
    login_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),   -- viaja en el JWT como claim "sid"
    user_id             BIGINT       REFERENCES users (users_id),
    attempted_email     VARCHAR(255) NOT NULL,
    result              VARCHAR(20)  NOT NULL
        CONSTRAINT chk_login_audit_result
            CHECK (result IN ('SUCCESS','FAILED_CREDENTIALS','FAILED_LOCKED','FAILED_INACTIVE')),
    roles               JSONB,               -- snapshot de roles al momento del login (solo SUCCESS)
    locale              VARCHAR(10),         -- solo SUCCESS
    ip_address          INET,
    user_agent          VARCHAR(500),
    hostname            VARCHAR(255),        -- best-effort
    jti                 VARCHAR(36),         -- solo SUCCESS; enlaza (sin FK física) con user_sessions_log.jti
    failure_reason      VARCHAR(255),
    session_status      VARCHAR(20)          -- ACTIVE | LOGGED_OUT | EXPIRED | REVOKED (histórico/reportes)
        CONSTRAINT chk_login_audit_session_status
            CHECK (session_status IN ('ACTIVE','LOGGED_OUT','EXPIRED','REVOKED')),
    session_expires_at  TIMESTAMPTZ,         -- login + refresh-expiration-days
    is_valid             BOOLEAN      NOT NULL DEFAULT TRUE,  -- única columna consultada en el hot path
    logged_out_at       TIMESTAMPTZ,
    logout_reason       VARCHAR(50),
    attempted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_audit_user_id      ON login_audit_log (user_id);
CREATE INDEX idx_login_audit_email        ON login_audit_log (attempted_email);
CREATE INDEX idx_login_audit_attempted_at ON login_audit_log (attempted_at DESC);
CREATE INDEX idx_login_audit_jti          ON login_audit_log (jti);
CREATE UNIQUE INDEX idx_login_audit_uuid  ON login_audit_log (uuid);
CREATE INDEX idx_login_audit_session_status ON login_audit_log (session_status) WHERE session_status = 'ACTIVE';
CREATE INDEX idx_login_audit_is_valid       ON login_audit_log (uuid) WHERE is_valid = TRUE;
```

**Sesión + claim `sid`**: en login exitoso la fila se inserta **antes** de generar los tokens (se reordena `AuthService.login()`), con `session_status='ACTIVE'` y `session_expires_at = now() + system_configs.login_session_expiration_days` (V71, configurable sin redeploy, default 30 — independiente de `jwt.refresh-expiration-days`, que solo controla el TTL del propio JWT). Su `uuid` se incluye como claim `sid` en access y refresh token (`JwtService.generateAccessToken`/`generateRefreshToken` ganan el parámetro `sessionId`). `roles`/`locale` son snapshot porque pueden cambiar después del login.

**Chequeo simple con `is_valid` (evita comparar fechas/estados en el hot path)**:
- `AuthService.logout()` marca `is_valid=false`, `session_status='LOGGED_OUT'`, `logged_out_at=now()` de inmediato.
- Un job de barrido (mismo patrón que `scheduled_job_runs`) corre cada pocos minutos: `UPDATE login_audit_log SET is_valid=false, session_status='EXPIRED' WHERE is_valid=true AND session_expires_at < now()`. Es la única pieza que compara fechas, y lo hace en background/lote.
- `JwtAuthenticationFilter` solo lee `is_valid` (cacheado, TTL corto, mismo mecanismo que la cache `audit-config`) por `sid`; si es `false` o no existe, rechaza el request. Esto cubre el reuso de un `sid` ya cerrado/expirado, como capa adicional sobre el blacklist de `jti` existente en `TokenBlacklistService`.
- Si la validación no puede resolverse (fila no encontrada, error de cache/BD): fail-safe — se loguea advertencia y no se bloquea el servicio; el blacklist de `jti` sigue siendo la garantía de seguridad independiente.

```sql
-- V62__data_change_audit_log.sql
SET search_path TO app, public;

CREATE TABLE data_change_audit_log
(
    data_change_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    entity_key               VARCHAR(80)  NOT NULL,
    entity_id                BIGINT,
    entity_uuid              UUID,
    action                   VARCHAR(20)  NOT NULL
        CONSTRAINT chk_data_change_action CHECK (action IN ('CREATE','UPDATE','DELETE')),
    before_json              JSONB,
    after_json               JSONB,
    actor_id                 BIGINT       REFERENCES users (users_id),
    login_audit_log_id       BIGINT       REFERENCES login_audit_log (login_audit_log_id),
    request_method           VARCHAR(10),
    request_path             VARCHAR(255),
    restored_from_id         BIGINT       REFERENCES data_change_audit_log (data_change_audit_log_id),
    occurred_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_data_change_entity       ON data_change_audit_log (entity_key, entity_uuid);
CREATE INDEX idx_data_change_entity_id    ON data_change_audit_log (entity_key, entity_id);
CREATE INDEX idx_data_change_actor        ON data_change_audit_log (actor_id);
CREATE INDEX idx_data_change_occurred_at  ON data_change_audit_log (occurred_at DESC);
CREATE INDEX idx_data_change_login_audit  ON data_change_audit_log (login_audit_log_id);
```

`restored_from_id` prepara una futura restauración: se modelaría como una nueva fila `UPDATE` con `after_json` = estado restaurado, apuntando a la fila histórica que la originó — sin requerir un mecanismo aparte.

```sql
-- V63__report_audit_log.sql
SET search_path TO app, public;

CREATE TABLE report_audit_log
(
    report_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    report_type          VARCHAR(80)  NOT NULL,
    entity_key           VARCHAR(80),
    entity_id             BIGINT,
    entity_uuid           UUID,
    entity_identifier    VARCHAR(120),
    format                VARCHAR(10)  NOT NULL
        CONSTRAINT chk_report_audit_format CHECK (format IN ('PDF','XLSX')),
    parameters_json       JSONB,
    actor_id              BIGINT       REFERENCES users (users_id),
    login_audit_log_id    BIGINT       REFERENCES login_audit_log (login_audit_log_id),
    attached_file_id      BIGINT       REFERENCES attached_files (attached_files_id),
    file_name             VARCHAR(255),
    size_bytes            BIGINT,
    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_report_audit_actor        ON report_audit_log (actor_id);
CREATE INDEX idx_report_audit_generated_at ON report_audit_log (generated_at DESC);
CREATE INDEX idx_report_audit_entity       ON report_audit_log (entity_key, entity_identifier);
CREATE INDEX idx_report_audit_entity_id    ON report_audit_log (entity_key, entity_id);
CREATE INDEX idx_report_audit_file         ON report_audit_log (attached_file_id);
```

```sql
-- V65__report_share.sql (esquema preparado, funcionalidad no implementada en este entregable)
SET search_path TO app, public;

CREATE TABLE report_share
(
    report_share_id     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    report_audit_log_id  BIGINT       NOT NULL REFERENCES report_audit_log (report_audit_log_id),
    share_token          VARCHAR(120) NOT NULL UNIQUE,
    share_link           VARCHAR(500),
    recipients           JSONB,
    expires_at            TIMESTAMPTZ  NOT NULL,
    revoked_at            TIMESTAMPTZ,
    created_by            BIGINT       REFERENCES users (users_id),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_report_share_report  ON report_share (report_audit_log_id);
CREATE INDEX idx_report_share_token   ON report_share (share_token);
CREATE INDEX idx_report_share_expires ON report_share (expires_at);
```

**Relación entre tablas** (mismo espíritu que `tseg_BITACORA_ACCESO`→`tseg_BITACORA_CAMBIO`/`tseg_REPORTE`): `login_audit_log` es la bitácora raíz de acceso; `data_change_audit_log` y `report_audit_log` cuelgan de ella vía `login_audit_log_id` (nullable para jobs/sistema).

```sql
-- V68__system_configs_audit_overrides.sql
SET search_path TO app, public;

ALTER TABLE system_configs
    ADD COLUMN data_change_audit_mode VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_data_change_audit_mode
            CHECK (data_change_audit_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN report_audit_mode      VARCHAR(20) NOT NULL DEFAULT 'PER_ENTITY'
        CONSTRAINT chk_system_configs_report_audit_mode
            CHECK (report_audit_mode IN ('PER_ENTITY', 'FORCE_ENABLED', 'FORCE_DISABLED')),
    ADD COLUMN login_audit_enabled    BOOLEAN     NOT NULL DEFAULT TRUE;
```

## Entidades JPA + repos

Paquete nuevo `core/audit/`:
- `core/audit/entity/{LoginAuditLog,DataChangeAuditLog,ReportAuditLog,AuditEntityConfig}.java` — insert-only (salvo `AuditEntityConfig`), JSONB vía `@JdbcTypeCode(SqlTypes.JSON)` (mismo patrón que `SubsidyAuditLog.before/after`).
- `core/audit/repository/*Repository.java` — finders por entidad/actor/fecha, análogos a `SubsidyAuditLogRepository.findBySubsidyIdOrderByCreatedAtDesc`.

## Aspecto AOP (`core/audit/aspect/DataChangeAuditAspect.java`) + `@Auditable`

Flujo del `@Around("@annotation(auditable)")`:
1. Consulta `AuditEntityConfigService.isEnabled(entityKey, action)` — cacheado (cache Redis `audit-config`, TTL 60s, junto a `catalogs`/`validator`/`users` en `RedisCacheConfig.cacheConfigs`). Si deshabilitado o **sin configuración** (fila ausente) → no audita, `log.warn(...)`, y sigue — nunca bloquea la operación de negocio.
2. Si UPDATE/DELETE y `capture_before_after=true`: snapshot "before" vía `EntitySnapshotResolver` por entidad (bean `Map<String, EntitySnapshotResolver>`, ej. `alliesService::getDetail`).
3. Ejecuta `joinPoint.proceed()`. Si lanza excepción: no audita, re-lanza.
4. Snapshot "after": para CREATE/UPDATE usa el DTO que el servicio ya retorna; para DELETE queda `null`.
5. Resuelve actor (`CustomUserDetails.getUuid()`) y sesión (`sid`) vía `AuditContextResolver` (reusado por el enganche de reportes).
6. Persiste en transacción separada (`REQUIRES_NEW`), con try/catch que nunca propaga fallos de auditoría hacia el llamador de negocio.

Aplicación en servicios (`AlliesService`, `MembersService`, extensible al resto): solo se agrega `@Auditable(entity="ally", action=..., uuidArgIndex=0)` sobre los métodos existentes.

## Login (`AuthService.java`) — **implementado**

`AuthService.login()` resuelve `ip`/`userAgent`/`hostname` una sola vez (vía `HttpServletRequest`) y llama a `LoginAuditService` en cada salida:

- `findByEmail` (no `findByEmailAndActiveTrue`) para poder distinguir email inexistente de cuenta inactiva a efectos de auditoría — la respuesta al cliente sigue siendo el mismo error genérico en ambos casos, solo cambia lo que se registra.
- **Email no encontrado** → `recordFailure(..., FAILED_CREDENTIALS, "email_not_found", ...)`, `userId=null`.
- **Cuenta inactiva** → `recordFailure(..., FAILED_INACTIVE, "account_inactive", ...)`.
- **Cuenta bloqueada** (`lockedUntil` futuro) → `recordFailure(..., FAILED_LOCKED, "account_locked", ...)`.
- **Password incorrecto** → `recordFailure(..., FAILED_CREDENTIALS, "bad_password", ...)` (además del contador `failedLoginAttempts` existente, sin relación entre ambos mecanismos).
- **Éxito** → `startSession(...)` inserta la fila **antes** de generar los tokens (`session_status=ACTIVE`, `session_expires_at = now() + system_configs.login_session_expiration_days` (configurable sin redeploy, default 30 — independiente de `jwt.refresh-expiration-days`, que solo controla el TTL del propio JWT), `roles`/`locale` como snapshot), devolviendo su `uuid` como `sid`; `JwtService.generateAccessToken`/`generateRefreshToken` ganan un parámetro `sessionId` que se embebe como claim `sid` (ambos tokens). Una vez generado el access token, `attachJti(sid, accessJti)` completa la fila. `refresh()` extrae el `sid` del refresh token entrante y lo propaga a los tokens reemitidos (misma sesión, no una nueva); `updateMyLocale` hace lo mismo vía `principal.getSessionId()` (nuevo campo en `CustomUserDetails`, poblado por `JwtAuthenticationFilter` desde el claim).
- **`login_audit_enabled=false`** (override global, Decisión 8): `startSession`/`recordFailure` no insertan nada; los tokens se emiten sin claim `sid`. `JwtAuthenticationFilter` trata la ausencia de `sid` como "nada que verificar" (nunca rechaza por eso) — el blacklist de `jti` sigue siendo la garantía de seguridad independiente.

`user_sessions_log` no se reemplaza — sigue siendo el registro operativo de sesiones vivas (`AuthService.logout()` sigue cerrándolo igual que antes).

**Chequeo `is_valid` en el hot path** — `JwtAuthenticationFilter` extrae el claim `sid` y llama a `LoginAuditService.isSessionValid(sid)`, respaldado por `LoginSessionValidityCache` (`@Cacheable("login-session")`, TTL 30s, mismo patrón self-invocation-safe que `AuditEntityConfigCache`). Contrato fail-safe: un `sid` desconocido (fila ausente) **sí rechaza** — es una señal legítima de token alterado; solo un error de infraestructura (excepción de BD/cache) falla abierto. Un token sin claim `sid` (auditoría deshabilitada al momento de emitirlo) no se verifica en absoluto.

`AuthService.logout()` llama a `LoginAuditService.closeSession(sid, "user_logout")` (`is_valid=false`, `session_status=LOGGED_OUT`, `logged_out_at=now()`), además de invalidar la cache. `LoginSessionSweepJob` (`@Scheduled`, cada 5 min por defecto — `audit.login-session-sweep.fixed-delay-ms`) hace el barrido en lote (`UPDATE ... SET is_valid=false, session_status='EXPIRED' WHERE is_valid=true AND session_status='ACTIVE' AND session_expires_at < now()`), la única pieza que compara fechas.

### Pendientes (backlog, no bloquean lo ya implementado)

- **`refresh()` no valida `isSessionValid(sid)` antes de reemitir tokens.** Solo chequea expiración del JWT y el blacklist de `jti`. Consecuencia: pasado el vencimiento de la sesión, `/refresh` igual "funciona" y emite tokens con el `sid` ya inválido — no es un hueco de seguridad (el access token resultante es rechazado por `JwtAuthenticationFilter` en el primer uso), pero es confuso para el cliente. Corrección: que `refresh()` llame a `loginAuditService.isSessionValid(sid)` y falle con un código explícito (`auth.session.expired`) en vez de emitir tokens muertos.
- **Política de expiración de sesión: deslizante + techo absoluto (recomendado), no solo fija.** Hoy `session_expires_at` se fija una única vez en el login (`now() + refresh-expiration-days`) y `refresh()` nunca la mueve — actividad constante no extiende la sesión, lo cual es correcto contra el riesgo de "sesión eterna" pero puede desloguear a un usuario activo a mitad de jornada. El patrón estándar (Auth0/Cognito) es: cada `refresh()` exitoso extiende `session_expires_at` (representa actividad), pero nunca más allá de un `absolute_expires_at` fijado en el login original (ej. 90 días) — requiere agregar esa segunda columna a `login_audit_log` y el cálculo `session_expires_at = min(now() + refresh-expiration-days, absolute_expires_at)` en `refresh()`.
- **Cierre por inactividad (24h sin actividad) aún no implementado** — requiere un `last_activity_at` en `login_audit_log` actualizado (con throttling, para no escribir en cada request) desde `JwtAuthenticationFilter`, y que `LoginSessionSweepJob` también expire por `last_activity_at < now() - 24h`, independiente del vencimiento por `session_expires_at`/`absolute_expires_at` de arriba.

## Reportes (`modules/document/generic/`) — **implementado**

`GenericDocumentController` (3 métodos: `/generic`, `/records/{entityOrTable}/{identifier}`, `/tables/{targetTable}`) llama explícitamente a `ReportAuditService.recordGeneration(...)` después de renderizar el documento, en su propia transacción `REQUIRES_NEW`. No es AOP (a diferencia de `DataChangeAuditAspect`): la generación de reportes no es un método CRUD de firma uniforme por entidad, y el controller ya tiene todo lo necesario (payload, formato, actor) en el punto de llamada.

- Resuelve primero el override global (`system_configs.report_audit_mode`, Decisión 8): `FORCE_DISABLED` nunca audita, `FORCE_ENABLED` siempre audita, `PER_ENTITY` consulta `audit_entity_config.audit_report` para el `entityKey` (si hay uno — un reporte sin entidad asociada, ej. `/generic` con payload libre, se audita igual bajo `PER_ENTITY`).
- Sube el contenido a R2 (bucket privado, `FileVisibility.TEMPORARY`, `ownerTable="report_audit_log"`, `ownerUuid=` el uuid recién generado de la fila) y crea el `AttachedFile` directamente (no vía `AttachedFileService`, que solo acepta `MultipartFile` — aquí el contenido ya es `byte[]` renderizado).
- Fail-safe (mismo criterio que el aspecto de cambios de datos, Decisión 6): si R2 está deshabilitado (`storage.r2.enabled=false`) o la subida falla, la respuesta HTTP igual entrega los bytes al cliente y la fila queda con `attached_file_id=NULL`. Si persistir la fila misma falla, se loggea (`log.warn`) y nunca se propaga al caller.
- `reportType` distingue el origen: `GENERIC` (`/generic`, sin entidad), `RECORD` (`/records/{entityOrTable}/{identifier}`, con `entityKey`/`entityUuid` cuando el identificador es un UUID), `TABLE` (`/tables/{targetTable}`, listado — sin `entityUuid` propio).

## Endpoints admin

- **`GET /v1/admin/audit/data-changes`** — **implementado.** `core/audit/controller/AdminDataChangeAuditController.java`, respaldado por `DataChangeAuditQueryService`. Un solo endpoint cubre tanto "historial completo de un registro" (`entityKey` + `entityUuid`) como filtrado cross-entity: query params `entityKey`, `entityUuid`, `actorUuid` (UUID público, resuelto internamente a `users_id` — nunca se filtra por el BIGINT directo), `action` (`CREATE`/`UPDATE`/`DELETE`), `from`/`to` (rango `occurredAt`, ISO-8601), y `filter` (RSQL contra un allowlist: `entityKey`, `entityId`, `entityUuid`, `action`, `requestMethod`, `requestPath`, `occurredAt`, `createdAt`). Paginado, tope de tamaño 200 vía `audit.page.size.exceeded`, protegido por `AUDIT_VIEW_ALL` (V69). `DataChangeAuditLogDto` no expone ningún BIGINT interno — `actorUuid` es el único identificador de actor.
  - **Response shape — `DataChangeAuditLogPageDto`**, no un `Page<T>` crudo: replica los mismos campos que Spring Data ya serializa (`content`, `pageable`, `last`, `totalElements`, `totalPages`, `size`, `number`, `first`, `sort`, `numberOfElements`, `empty`) **más `firstChange`** — la fila más antigua (típicamente el CREATE) del registro consultado, embebida en la misma respuesta, mismo request. Inspirado en `ListEntityLogsResponse.first_entity_log` de `adempiere-grpc-server` (`logs.proto`): así una entidad con 500+ cambios siempre puede mostrar "creado el ..." sin paginar hasta el final ni pegarle un segundo request al backend. `firstChange` solo se llena cuando la consulta está acotada a un registro (`entityKey` + `entityUuid` ambos presentes) — en el listado cross-entity sin acotar queda `null` ("¿el primer cambio de qué?" no aplica ahí).
  - **`GET /v1/admin/audit/data-changes/first-change?entityKey=&entityUuid=`** — endpoint hermano no paginado (mismo patrón que `AdminSubsidyController#/{uuid}/log`), se mantiene disponible para cuando el cliente solo necesita ese dato puntual (ej. mostrar "creado el ..." antes de siquiera abrir la vista paginada). 404 (`audit.first_change.not_found`) si no hay historial.
  - **Valores de visualización en `DataChangeAuditLogDto`** (resueltos por `AuditDisplayResolver` — nunca bloquean la respuesta, degradan a ausencia del sibling `_Display` si no se puede resolver): `entityDisplay` (label actual del registro propio, ej. el `name` de un `Ally`), `action_Display` (`CREATE`/`UPDATE`/`DELETE` traducido vía `MessageSource`, código `audit.action.<ACTION>`, respeta el locale del request), `actor_Display` (nombre completo del actor, viaja siempre junto a `actorUuid` — nunca uno sin el otro).
  - **`beforeJson`/`afterJson`**: cada valor gana un sibling `<campo>_Display` (sufijo `_Display`, no toca el original) según su forma, en este orden:
    1. `Boolean` → `"Sí"/"No"` (es) o `"Yes"/"No"` (en), vía `audit.value.true`/`audit.value.false`.
    2. `String` que parsea como `UUID` → registry de FKs (`cityUuid`, `allyTypeUuid`, `planUuid`, `promoterUuid`, …) — cobertura parcial intencional (22 entidades + FKs de catálogo comunes).
    3. `String` que parsea como fecha/instant ISO-8601 → formato de país (Venezuela, ADR 0010): `dd-MM-yyyy HH:mm` (es) / `MM-dd-yyyy HH:mm` (en), convertido a `America/Caracas` (UTC-4, sin DST) — mismo patrón sin hora si es solo fecha (`yyyy-MM-dd`).
    4. `Number` → separadores de miles/decimal según locale (`NumberFormat`), preservando la precisión real del valor (un entero no gana decimales; un `BigDecimal`/`double` con fracción conserva su propia escala — `12.50` se ve `12,50` en es, no `12,5`).
    5. `String` con forma `UPPER_SNAKE_CASE` (constante de enum) → traducido vía `audit.enum.<campo>.<valor>`, con fallback a un vocabulario compartido `audit.enum.common.<valor>` (`ACTIVE`, `PENDING`, `APPROVED`, `IN_REVIEW`, …); si ninguna clave existe, no se agrega `_Display` (evita traducciones inventadas).
    6. Cualquier otro string libre (nombres, emails, notas) → sin `_Display`, ya es legible.
- **`GET /v1/admin/audit/reports`** — **implementado.** `core/audit/controller/AdminReportAuditController.java`, respaldado por `ReportAuditQueryService`. Query params: `reportType` (`GENERIC`/`RECORD`/`TABLE`), `entityKey`, `entityUuid`, `actorUuid`, `format` (`PDF`/`XLSX`), `from`/`to` (rango `generatedAt`), `filter` (RSQL contra un allowlist: `reportType`, `entityKey`, `entityId`, `entityUuid`, `format`, `generatedAt`, `createdAt`). Paginado (`Page<ReportAuditLogDto>` estándar de Spring — no necesita el wrapper `firstChange` de `data-changes`, ya que "el primer reporte generado" no es un concepto que este endpoint necesite resolver), tope 200, protegido por `REPORT_AUDIT_VIEW_ALL` (V70, mismo patrón que `AUDIT_VIEW_ALL`/V69 pero para `report_audit_log`).
  - **`GET /v1/admin/audit/reports/{uuid}/download`** — **implementado.** Devuelve `{"url": "..."}` con una URL presignada (15 min) al archivo almacenado; 404 (`audit.report.not_found`/`audit.report.file_not_found`) si la fila no existe o su `attached_file_id` es `NULL` (upload nunca ocurrió o falló en el momento de generar el reporte).
- **`GET /v1/admin/audit/logins`** — **implementado.** `core/audit/controller/AdminLoginAuditController.java`, respaldado por `LoginAuditQueryService`. Filtros: `email`, `userUuid`, `result` (`SUCCESS`/`FAILED_CREDENTIALS`/`FAILED_LOCKED`/`FAILED_INACTIVE`), `from`/`to` (rango `attemptedAt`), `filter` (RSQL contra un allowlist: `attemptedEmail`, `result`, `userId`, `sessionStatus`, `valid`, `attemptedAt`, `createdAt`). Paginado (`Page<LoginAuditLogDto>` estándar), tope 200, protegido por `AUDIT_VIEW_LOGIN` (V64, ya existente). `LoginAuditLogDto.result_Display` es el resultado traducido vía `MessageSource` (`audit.login.result.<RESULT>`).
- `GET /v1/admin/audit/config` y `PATCH /v1/admin/audit/config/{entityKey}` con `@CacheEvict` sobre `audit-config`. — **sin implementar** (el servicio `AuditEntityConfigService`/`AuditEntityConfigCache` ya existen y quedan listos para que este endpoint los use).

## Permisos granulares por dominio

Mismo patrón de `V54__document_permissions.sql` (`permissions`/`permission_domains`/`role_permissions`, SYSTEM vía trigger V30):

- **`<DOMAIN>_AUDIT_VIEW`** — ver el historial de cambios (timeline) de un registro de esa entidad.
- **`<DOMAIN>_AUDIT_RESTORE`** — restaurar a un punto del historial (permiso creado, funcionalidad **no implementada** en este entregable).
- **`<DOMAIN>_REPORT_GENERATE`** — generar reporte sobre esa entidad.
- **`REPORT_SHARE`** (único, no por dominio) — crear enlace de `report_share` (permiso y tabla listos, funcionalidad **no implementada**).

## Mecanismo `DisplayFormatter` — convención `_Display` generalizada

> Congela [hub ADR 0014](../../../centro-optico-vicente/.ai/decisions/0014-display-value-convention.md).
> El sufijo `_Display` deja de ser exclusivo de auditoría: pasa a ser la forma única en que **cualquier**
> list/detail DTO expone etiquetas de FK y escalares presentacionales (fecha, fecha-hora, monto,
> decimal, enum, boolean), resueltos por `Locale` en el servidor. Mecanismo `@Display` +
> `BeanSerializerModifier` implementado; migración por módulo en curso (ver §Rollout — estado).

### `core/display/DisplayFormatter` (`@Component`) — autoridad única de presentación

- **Escalares:** formatea por tipo (fechas, montos `VES`, decimales, porcentajes, enums, booleanos).
  Todo lo que varía por idioma sale del `MessageSource`, **incluido el patrón de fecha**
  (`display.format.datetime` / `display.format.date`, parseado y cacheado por patrón) — un idioma
  nuevo es alta de bundle, no de código. Enums/booleanos → `display.enum.<campo>.<VALOR>` con
  fallback `display.enum.common.<VALOR>` y `audit.enum.common.<VALOR>`; `display.boolean.true|false`.
  Números/moneda → `NumberFormat` por `Locale`. La zona de display sale de la env `TZ` (fallback
  `America/Caracas`, ADR 0010, si falta o es inválida). Detector `ENUM_SHAPED` reusado de
  `AuditDisplayResolver`.
- **FK:** formatea a partir de la **entidad asociada ya cargada**. `Map<String, Function<Object,
  String>>` (clave = `<rel>` lógico) poblado en el constructor + **default reflexivo** alineado con
  `GenericEntityExtractorService.extractDisplayStringFromObject`
  (`getFullName → getDisplayName → getName → getCode`; si todo falla → `null`, nunca el UUID).
  Overrides iniciales = las lambdas actuales de `AuditDisplayResolver` (catálogos tipados
  `code + " - " + name`; `person` y derivados `taxDocumentNumber + " " + fullName`; `membership`
  `person.fullName + " — " + plan.name`; `corporateContract` `institutionName`; etc.).
- **No hace queries.** Recibe siempre el valor / la asociación ya materializados.

### Emisión — anotación `@Display` + `BeanSerializerModifier` (implementado)

Los DTOs son `record` inmutables → nada de `xxx_Display` campo por campo. La emisión es en
serialización:

- **`core/display/Display`** — anotación sobre el componente del `record`:
  `@Display(Kind.MONEY|DATETIME|DATE|NUMBER|PERCENT|ENUM|BOOLEAN)` para escalares
  (`Kind.AUTO` infiere por tipo runtime: temporal→datetime, `Boolean`→sí/no, `Number`→separadores,
  `String`→enum); `@Display(fk = "allyType")` **o** un campo tipado `DisplayRef` para FK;
  `enumScope` fija la clave i18n (`display.enum.<scope>.<VALOR>`).
- **`core/display/DisplayRef(uuid, code, name)`** — carrier compacto de una FK dentro del DTO.
  Nunca se serializa como objeto.
- **`core/display/DisplayRefs`** (`@Component`) — construye el `DisplayRef` desde la asociación ya
  cargada, por reflexión (`getUuid` + `getCode` + primero de
  `getFullName/getDisplayName/getName/getInstitutionName`), con overloads tipados `ref(Person)`
  (doc + nombre) y `ref(User)` (email). Los mappers lo traen con `@Mapper(uses = DisplayRefs.class)`
  → un target `SomeEntity → DisplayRef` resuelve sin boilerplate por mapper.
- **`core/display/DisplayBeanSerializerModifier`** — registrado en el `ObjectMapper` `@Primary`
  (`core/config/JacksonConfig`). Para cada campo `@Display`:
  - FK (`DisplayRef` o `fk` no vacío): reemplaza el campo por `<rel>_Uuid` + `<rel>_Display`; el
    objeto `DisplayRef` **no** se emite.
  - Escalar: deja el valor crudo y agrega `<campo>_Display`.
  - Respeta `@JsonInclude(NON_NULL)` (`willSuppressNulls()`): en un DTO NON_NULL los siblings nulos
    se omiten; en un DTO normal se escriben siempre (incluso `null`).
- **`DisplayFormatter.fkLabel(rel, ref)`** — `"code - name"` para catálogos tipados
  (`allyType`, `gender`, `maritalStatus`, `promoterType`, `medicalSpecialty`, `serviceCategory`,
  `documentType`, `occupation`, `country`, `state`); `"doc fullName"` para relaciones tipo persona
  (`person`, `member`, `beneficiary`, `holder`, `titular`, `reviewedBy`, `actor`); resto → `name`,
  si no `code`, si no `null` (nunca el UUID).
- El modifier **no hace queries**: el `DisplayRef` lo arma el mapper desde la asociación que ya
  trae el `findAll(spec, pageable)` → sin N+1.
- `_Display` / `<rel>_Uuid` derivado nunca se aceptan en un request (no son componentes del
  `record`; Jackson los ignora al deserializar). El `<rel>_Id` BIGINT sigue **interno**.
- Test sin Spring: `core/display/DisplayBeanSerializerModifierTest` serializa un DTO de muestra
  contra los bundles reales (par FK plano, sin objeto anidado, siblings escalares, nulos, es/en,
  NON_NULL).

### Alineación con el motor de reportes

`GenericEntityExtractorService.formatValue` / `SPANISH_ENUM_LABELS` (Jasper/PDF) y `DisplayFormatter`
**no comparten código** pero deben coincidir en formato de escalares y en el orden del extractor
reflexivo de etiqueta; al implementar, revisar ambos para no divergir.

### Rollout — estado

Un solo branch (`feature/display-value-convention-rollout`), un commit por módulo. **Backend
únicamente** — cada endpoint renombrado rompe su consumidor de frontend, que se migra después.

| Módulo | DTO(s) | Alcance aplicado |
|---|---|---|
| **ally** | `AllyListItemDto` | FK `allyType`/`city` → `DisplayRef`; escalares `_Display`; `taxDocument*` crudos. `AllyMapper.toListItem` con `uses = DisplayRefs`. `SORTABLE_FIELDS` alias `allyType_Display`/`city_Display`. |
| **promoter** | `PromoterDto` (list + detail) | FK `user`/`person`/`promoterType` → `DisplayRef`; escalares `_Display`. Mapper sin `@Mapping` planos. |
| **member** | `MemberListItemDto` | `cityName` → `city` `DisplayRef`, `currentPromoter*` → `currentPromoter` `DisplayRef`; escalares `_Display`. `MemberDetailDto` **intacto** (edit-response, mantiene DTOs de catálogo anidados, como `AllyDetailDto`). |
| **membership** | `MembershipDto` | Solo escalares `@Display` (montos, fechas, `planType`, `status`, `active`). FK plano `plan*` intacto (expone code+name+type — no es colapso limpio). |
| **payment** | `PaymentDto` | Solo escalares `@Display` (montos, método, fechas, `status`, flags). FK planos intactos. |
| **auth** | `UserDto`, `RoleUserDto` | Solo escalares `@Display` (`status`, `active`, `lastLoginAt`) — `UserDto` es edit-response (mantiene person-flat + `roles` anidado). |
| **ally (pivots)** | `AllyUserDto`, `UserAllyDto`, `MyAllyDto` | `AllyUserDto`: escalares. `UserAllyDto`: `ally*` → `DisplayRef ally`. `MyAllyDto`: `allyType*` → `DisplayRef allyType`. `DisplayRefs` pasó a utilidad `static` (MapStruct lo llama sin inyección → mappers usables en unit tests). |

| **resto** (escalares `@Display` aditivos, sin renombrar campos) | `CommissionDto`, `CommissionTierDto`, `BonusRuleDto`, `BonusAwardDto`, `CollectionCommissionTierDto`, `LeaderboardEntryDto`, `MyReferralDto`, `SubsidyDto`, `SubsidyBeneficiaryDto`, `CorporateContractDto`, `BenefitUsageDto`, `DigitalCardDto`, `ScheduledJobDto`, `EntityConfigDto`, `ReportAuditLogDto`, `ValidationResultDto`, `MemberPromoterAssignmentDto` | montos / fechas / enums / booleanos con `_Display`. Pares `<rel>Uuid`+`<rel>Name` intactos (exponen varios atributos de una relación); `pct` → `NUMBER` (sin `%` falso). |

Regla aplicada: **list DTOs y proyecciones de solo lectura** migran (con colapso FK →
`<rel>_Uuid` + `<rel>_Display` cuando la relación aporta un par `uuid (+ name/code)` limpio);
**detail/edit-response** (mantienen DTOs de catálogo anidados) y **request DTOs** no; si el DTO usa
3+ atributos independientes de una relación, solo escalares `_Display`.

DTOs públicos: `PublicPlanDto` (fees `MONEY`, `type` `ENUM`), `PublicAllyServiceDto` /
`PublicServiceListItemDto` (`priceUsd`/`discountPct` `NUMBER` — es USD, no `VES`;
`requiresAppointment` `BOOLEAN`). `PublicAllyListItemDto` / `PublicAllyDetailDto` no tienen escalar
que anotar (money/fechas/status excluidos a propósito de la forma sanitizada).

**Pendiente:** refactor de `AuditDisplayResolver` para delegar el formato en `DisplayFormatter`;
`./gradlew build` completo + ITs (Postgres). Los tests unitarios de los módulos tocados +
`DisplayBeanSerializerModifierTest` están en verde.

### Refactor de `AuditDisplayResolver` (pendiente)

Sigue standalone; el paso de delegar su *formato* en `DisplayFormatter` queda para después (ver
arriba). Ambos ya comparten claves i18n.

## Archivos críticos

- `modules/auth/service/AuthService.java` (login, líneas 76-113)
- `modules/auth/service/JwtService.java` (claim `sid`)
- `modules/document/generic/controller/GenericDocumentController.java`
- `core/config/RedisCacheConfig.java` (línea 27 `@Profile("prod")`, línea 55 `cacheConfigs`)
- `modules/ally/service/AlliesService.java`, `modules/member/service/MembersService.java`
- `db/migration/V41__subsidies.sql`, `V54__document_permissions.sql` (plantillas)
- `security/CustomUserDetails.java`, `security/JwtAuthenticationFilter.java`
- `modules/subsidy/**` (patrón `SubsidyAuditLog` a replicar)
- `modules/system/entity/SystemConfig.java`, `service/SystemConfigService.java`, `controller/SystemConfigController.java`, `dto/{SystemConfigDto,UpdateSystemConfigRequest}.java` (Decisión 8 — override global; ya existen, faltan extender con `dataChangeAuditMode`/`reportAuditMode`/`loginAuditEnabled`)

## Verificación

- `AlliesService` create/update/delete generan filas correctas en `data_change_audit_log`; con `enabled=false` o sin config no se inserta nada y la operación de negocio completa igual; si el método lanza excepción, no se audita.
- Login exitoso deja `session_status=ACTIVE` con `sid` en el JWT; `logout()` la pasa a `LOGGED_OUT`/`is_valid=false`; un JWT con `sid` inválido/expirado es rechazado por `JwtAuthenticationFilter`.
- Permisos granulares: sin `ALLY_AUDIT_VIEW` → 403 en el endpoint de timeline; con el permiso, ve el historial. Mismo patrón para `ALLY_REPORT_GENERATE`.
- Generación de reporte crea fila en `report_audit_log` con `attached_file_id` resuelto; si `StorageService` falla, el archivo se entrega igual y el log queda con `attached_file_id=NULL`.
- Cache de `AuditEntityConfigService`: sirve valor dentro del TTL y `@CacheEvict` refresca tras un `PATCH`.
- Override global (Decisión 8): con `data_change_audit_mode=FORCE_DISABLED`, ninguna entidad audita cambios aunque su fila en `audit_entity_config` diga `enabled=true`; con `FORCE_ENABLED`, todas auditan aunque su fila diga `enabled=false`; con `PER_ENTITY` (default) el comportamiento es el de hoy. Mismo patrón para `report_audit_mode`. Apagar `login_audit_enabled` deja de insertar en `login_audit_log` en cada login (comportamiento de `sid`/JWT a definir en la implementación del hook).
- `./gradlew build` verde + flujo manual (login fallido, creación de un aliado, generación de reporte) confirmando las tablas nuevas vía SQL directo.
