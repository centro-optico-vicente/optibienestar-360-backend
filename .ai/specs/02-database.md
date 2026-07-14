# 02 — Schema PostgreSQL completo

> Schema detallado de todas las tablas. Convenciones en [ADR 0006 cross-stack](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md).
>
> **Estado:** este archivo se irá completando conforme se ejecuten las migraciones de la Tarea 5.1.

## Diagrama conceptual

Ver [hub `05-domain-model.md`](../../../centro-optico-vicente/.ai/specs/05-domain-model.md) para el diagrama ER de alto nivel.

## Catálogo de migraciones

### Aplicadas

| Migration | Tablas/cambios |
|---|---|
| `V1__initial_extensions.sql` | pgcrypto, unaccent, citext, schema `app` |
| `V2__base_audit_function.sql` | función `set_updated_at()` |
| `V3__app_roles.sql` | DB roles `optibienestar360_migration`/`optibienestar360_app`/`optibienestar360_readonly` con CONNECTION LIMIT + statement_timeout + idle_in_transaction_timeout; REVOKE PUBLIC; ALTER ROLE search_path |
| `V4__contact_messages.sql` | contact_messages (landing) |
| `V5__users_and_roles.sql` | security_policies, roles, **permission_domains** (10 seed), permissions (con `domain_id` FK), users, user_password_history, user_roles, role_permissions, user_sessions_log |
| `V6__seed_roles.sql` | 7 roles + 50 permisos (incluye `ROLE_PERMISSION_EDIT`) + asignación rol→permisos + security_policies default |
| `V7__seed_users.sql` | 2 usuarios seed (System, Administrador) |
| `V8__locations.sql` | countries, states, cities (seed Venezuela 24 estados + capitales) |
| `V9__personal_catalogs.sql` | genders, document_types, marital_statuses, occupations |
| `V10__health_catalogs.sql` | medical_specialties, service_categories, ally_types |
| `V11__allies.sql` | allies, ally_specialties, ally_services, ally_agreements + ally_service_review_log + workflow PROPOSED/IN_REVIEW/APPROVED/REJECTED/REMOVED + permiso `ALLY_SERVICE_APPROVE` + publishing flags |
| `V12__ally_users.sql` | ally_users (N:M pivote con `ally_role` enum + `is_primary`) + dropea `allies.manager_user_id` redundante |
| `V13__plans.sql` | plans con campos v2 (type enum INDIVIDUAL/FAMILIAR/CORPORATIVO + included/max_beneficiaries + extra_beneficiary_inscription_fee + publishing) |
| `V14__seed_plans.sql` | 3 SKUs del flyer: Individual ($10/$5, published), Familiar ($20/$5, published), Corporativo ($5/$5, unpublished — pricing TBD) |
| `V15__persons.sql` | **persons** — identity hub (nombres partidos, doc, RIF, contacto, address, city_id FK) |
| `V16__refactor_users_persons.sql` | backfill persons desde users + ALTER users (drop demográficos, add `person_id` FK NOT NULL UNIQUE) |
| `V17__members.sql` | members (FK person_id, occupation, enrolled_at) + member_documents |
| `V18__beneficiaries.sql` | beneficiaries (FK person_id; UNIQUE(member_id, person_id); v2 extra_inscription_paid + inscription_payment_id BIGINT — FK diferido aplicado en V23) |
| `V19__medical_records.sql` | medical_records 1:1 con persons (audiencia restringida: MEDICAL_RECORD_VIEW/UPDATE) |
| `V20__bcrypt_helper.sql` | función `app.bcrypt_hash(plain, strength)` compat con `BCryptPasswordEncoder` — permite seeds con plaintext password |
| `V21__memberships.sql` | memberships con lifecycle ACTIVE/SUSPENDED/EXPIRED/CANCELED + pricing snapshot + partial UNIQUE 1-activa-por-member |
| `V22__scheduled_jobs.sql` | scheduled_jobs + scheduled_job_runs (audit ledger JSONB) + dominio SCHEDULED_JOBS con 5 permisos `JOB_*` + seed MEMBERSHIP_STATUS_SWEEP |
| `V23__payments.sql` | payments workflow manual (PENDING/APPROVED/REJECTED) con proof of payment R2 + allocation inscription/recurring + 4 CHECK coherence + 3 índices; wires FK diferido `beneficiaries.inscription_payment_id` → `payments` |
| `V24__benefit_usages.sql` | benefit_usages audit ledger (membership/ally/service/ally_user FKs) + dual time + copay pareado + metadata JSONB per-ally type (clinic/optical/pharmacy) + status (REGISTERED/REVERSED/DISPUTED) + 4 índices |
| `V25__promoters.sql` | promoters con v2 baked-in (referral_code UNIQUE + is_system) + seed INSTITUCION + agrega `members.promoter_id` NULLABLE FK + 3 índices (partial unique system row, partial unique user_id, leaderboard de reales) |
| `V26__commissions.sql` | commissions audit-ledger con promoter+payment+member FKs + calculation snapshot (pct XOR flat + tier name) + period bounds inclusive + workflow PENDING/PAID/VOIDED/DISPUTED + 5 CHECK coherence + 4 índices (partial unique non-voided per pago/promotor, liquidation, member history, payment fan-out) |
| `V27__referrals.sql` | referrals afiliado-a-afiliado (referrer+referred FKs, lifecycle 5-state, reward pct XOR flat) + 8 CHECK coherence + 5 índices + `members.referral_code` UNIQUE partial |
| `V28__notifications.sql` | notifications cola persistente outbound (channel EMAIL v1, recipient snapshot CITEXT, template_code open + JSONB vars, source_module + source_entity_uuid para dedup, status PENDING/SENDING/SENT/FAILED/DEAD_LETTER, retry policy con attempt_count/max_attempts/next_retry_at + last_error_message) + 4 CHECK coherence + 5 índices partial (worker poll, retry poll, recipient history, source dedup, dead-letter triage) + dominio NOTIFICATIONS con 3 perms (`NOTIFICATION_VIEW_ALL`/`RESEND`/`VIEW_OWN`) — VIEW_OWN distribuido a AFILIADO+PROMOTOR+ALIADO+SYSTEM+ADMINISTRADOR (mirror REFERRAL_CODE_VIEW_OWN) |

### Planeadas

> 📌 **Renumeración del 2026-06:**
> - 1ª: se insertaron `V15__persons.sql` y `V16__refactor_users_persons.sql` (persons hub central + extracción de demográficos de users). Todo lo que era V15+ se bumpeó +2. Ver [ADR 0011](../decisions/0011-persons-identity-hub.md).
> - 2ª: se insertó `V20__bcrypt_helper.sql` (utilidad para seeds). Todo lo que era V20+ planeado se bumpeó +1. memberships pasó de V20 a V21.
> - 3ª: se insertó `V22__scheduled_jobs.sql` (framework de tareas programadas — cron config + audit ledger + permisos `JOB_*`). Todo lo que era V22+ planeado se bumpeó +1. payments pasó de V22 a V23.

| Migration | Tablas/cambios | Vertical |
|---|---|---|
| `V29__digital_cards_view.sql` | vista digital_cards_v | 9 — notificaciones |
| `V30__audit_log.sql` | tabla audit_log | 10 — hardening |
| `V31__indexes_optimization.sql` | índices adicionales según EXPLAIN | 10 — hardening |

## Decisiones diferidas y pendientes de análisis

> **Patrón general del proyecto** (aplicable a toda migración / schema work, no solo a una vertical específica): cada vez que una migración deja un **FK diferido**, un **trigger no implementado**, una **columna `_id` sin la tabla destino**, una **política de snapshot vs live** a confirmar, o una **decisión de diseño** que requiere más data productiva para validarse — se captura acá, agrupado por la migración que originó la deuda. Se revisan cuando hay ventana de análisis; **no bloquean el flujo principal**.
>
> El bullet ideal es **resolvible**: tiene un trigger claro de cierre (*"cuando V27 traiga `commission_tiers`..."*) o una decisión explícita a tomar (*"DB trigger vs service code"*). Si el item solo dice *"revisar X"* sin condición de cierre, hay que afinarlo o eliminarlo.
>
> **Convención de localización**: esta sección documenta solo la **doctrina general** (la regla del proyecto). Los **items específicos por migración** viven en el checklist de la vertical que owns esa migración — son los devs de esa vertical los que mejor pueden cerrar el item al implementar el próximo bullet. Cada checklist de vertical que tenga deuda diferida abre su sección **"## Pendientes de análisis / Decisiones diferidas"** con un cross-ref a esta doctrina y la lista de items resolvibles agrupados por migración.

### Índice por migración → vertical donde viven los items

| Migración | Vertical | Sección del checklist |
|---|---|---|
| `V25__promoters.sql` | 8 — promotores | [vertical-8 § Pendientes de análisis](../checklists/vertical-8-promotores-comisiones-referidos.md#pendientes-de-an%C3%A1lisis--decisiones-diferidas) |
| `V26__commissions.sql` | 8 — promotores | [vertical-8 § Pendientes de análisis](../checklists/vertical-8-promotores-comisiones-referidos.md#pendientes-de-an%C3%A1lisis--decisiones-diferidas) |
| `V27__referrals.sql` | 8 — promotores | [vertical-8 § Pendientes de análisis](../checklists/vertical-8-promotores-comisiones-referidos.md#pendientes-de-an%C3%A1lisis--decisiones-diferidas) |

> Cuando una nueva migración deja deuda diferida, agregar (a) el bullet o sección en su vertical, y (b) una fila en este índice para trazabilidad cross-vertical.

## Convenciones aplicadas a TODAS las tablas

Por [ADR 0006](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md):

```sql
CREATE TABLE example (
    example_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    -- specific columns...
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    status          VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      UUID            NULL REFERENCES users(user_id),
    updated_by      UUID            NULL REFERENCES users(user_id)
);
CREATE TRIGGER trg_example_before_update_set_updated_at
    BEFORE UPDATE ON example
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

## Detalle por tabla

> Cada sección se completa al ejecutar la migración correspondiente. Documentar: propósito, columnas no-obvias, índices, constraints especiales, decisiones de tipos.

### `users`

(pendiente — al ejecutar V10)

### `roles`, `permissions`, `permission_domains`, `user_roles`, `role_permissions`

**Aplicadas en V5 (esquema RBAC + catálogo de dominios) y V6 (seed roles + 50 permisos incluido `ROLE_PERMISSION_EDIT`).**

- **`roles`** (7 filas seed): `SYSTEM`, `ADMINISTRADOR`, `OPERADOR`, `OPERADOR_MEDICO`, `ALIADO`, `AFILIADO`, `PROMOTOR`. Columnas: `name` UNIQUE, `description`, audit estándar.

- **`permissions`** (50 filas seed V6): catálogo code-bound. Columnas: `name` UNIQUE (string técnico tipo `MEMBER_CREATE`, evaluado en `@PreAuthorize`), `domain_id` FK → `permission_domains`, `description` (texto humano en español que ve el admin), audit.
  - **Regla clave:** este catálogo es inmutable en runtime; cada permiso requiere un `@PreAuthorize("hasAuthority('NAME')")` que lo respalde. Solo se modifica vía migración + deploy.

- **`permission_domains`** (10 filas seed V5): módulos UI para agrupar permisos en el panel admin. Columnas: `code` UNIQUE (técnico, `'USERS'`/`'MEMBERS'`/...), `name` UNIQUE (label español: `'Usuarios'`/`'Afiliados'`/...), `icon` (clase lucide), `description` (texto largo para tooltip), `display_order` (orden en el panel), audit.

- **`user_roles`** (pivot): `user_id` × `role_id`, con `assigned_by`, `expires_at`, `is_active`. UNIQUE (user_id, role_id).

- **`role_permissions`** (pivot): `role_id` × `permission_id`. UNIQUE. **Esta tabla SÍ es editable en runtime** desde el panel admin (vía `PUT /v1/admin/roles/{uuid}/permissions`, pendiente) — los permisos asignados a cada rol pueden cambiar sin redeploy.

Spec funcional: [`05-roles-permissions.md`](05-roles-permissions.md).

### `members`, `beneficiaries`, `medical_records`

(pendiente)

### `allies`, `ally_services`, `ally_agreements`, `ally_users`

(pendiente)

### `plans`, `memberships`

(pendiente)

### `payments`

(pendiente)

### `benefit_usages`

(pendiente)

### `promoters`, `commissions`, `referrals`

(pendiente)

### `notifications`

Cola persistente de notificaciones outbound. La aplicación enquea una fila por evento (welcome email, recordatorio de pago, membresía suspendida, recompensa de referido, recibo de payout de comisión, etc.); un worker (`NotificationService`, vertical-9) pollea filas PENDING cuyo `scheduled_for` ya pasó, renderiza el template, y entrega vía el canal (v1: SMTP/EmailService).

**Por qué cola persistente vs el path previo "fire-and-forget @Async"**: sobrevive a restart del JVM (no se pierde mail enqueueado en deploy), retry policy con exponential backoff visible en la fila, audit trail per-recipient y per-source, admin queue UI puede mostrar "qué falló, por qué, reintentarlo".

**Lifecycle** (`status`):

```
PENDING  →  SENDING        (worker reclamó la fila)
SENDING  →  SENT           (canal ACKeó delivery)
         →  FAILED         (error de delivery, next_retry_at fijado si quedan attempts, sino DEAD_LETTER)
FAILED   →  SENDING        (ciclo de retry la levanta otra vez)
         →  DEAD_LETTER    (attempt_count alcanzó max_attempts)
```

**Diseño clave**:

- **`channel`** whitelisted (v1 `EMAIL` only). Agregar `SMS` / `PUSH` después es un ALTER + CHECK update, no un rewrite.
- **`recipient_email CITEXT`** + **`recipient_user_id BIGINT NULL`**: el email es snapshot al enqueue (cambios posteriores no redirigen mail ya queueado); el FK opcional preserva navegabilidad para admin drill-downs y para el endpoint `VIEW_OWN`.
- **`recipient_locale`**: 'es' / 'en' — pickea la variante `*_es.html` / `*_en.html` del template.
- **`template_code VARCHAR(80)` open** sin CHECK whitelist — mismo patrón que `scheduled_jobs.code`: resuelve a Thymeleaf template en classpath al render, nuevos templates landean por archivo, no por migración.
- **`subject VARCHAR(200)`** pre-renderizado al enqueue desde i18n bundle. Stored inline para que el queue se replay sin re-resolver message keys; templates se re-renderizan per-attempt pero el subject queda fijo desde enqueue.
- **`template_vars JSONB`** per-template (e.g. `payment-approved` lleva `{paymentDate, amount, currency}`, `commission-payout` lleva `{periodStart, periodEnd, total, csv}`). Hibernate 6 mapea con `@JdbcTypeCode(SqlTypes.JSON)` sobre `Map<String,Object>`.
- **`source_module VARCHAR(40)`** + **`source_entity_uuid UUID`**: traceability + idempotency. Composite `(source_module, source_entity_uuid, template_code)` permite check "ya enqueueamos `payment-approved` para este payment?" sin necesidad de columna dedicada `idempotency_key`.
- **Retry policy per-row**: `max_attempts` configurable (alerta crítica one-shot → `max_attempts=1`; recordatorio rutinario → `max_attempts=5`, service default). `next_retry_at` fijado por el service en exponential backoff (60s × 2^(attempt-1) clamped a 1h típicamente).

**4 CHECK constraints de coherencia**:

1. `chk_notifications_sent_has_sent_at` — `SENT` ⇒ `sent_at NOT NULL`.
2. `chk_notifications_failed_has_error` — `FAILED`/`DEAD_LETTER` ⇒ `last_error_message NOT NULL`.
3. `chk_notifications_attempt_bounds` — `attempt_count <= max_attempts`.
4. `chk_notifications_dead_letter_exhausted` — `DEAD_LETTER` ⇒ `attempt_count >= max_attempts`.

**5 índices partial** (todos `WHERE` filtrados para mantenerlos chicos a escala):

| Índice | Predicado | Para |
|---|---|---|
| `idx_notifications_pending_due` | `(scheduled_for) WHERE status='PENDING' AND is_active` | Worker poll |
| `idx_notifications_retry_due` | `(next_retry_at) WHERE status='FAILED' AND next_retry_at IS NOT NULL AND is_active` | Retry poll (separado del PENDING para no competir scans) |
| `idx_notifications_recipient_created` | `(recipient_user_id, created_at DESC) WHERE recipient_user_id IS NOT NULL AND is_active` | "Todo lo enviado al usuario X, más reciente primero" — admin drill + `VIEW_OWN` |
| `idx_notifications_source` | `(source_module, source_entity_uuid, template_code) WHERE source_entity_uuid IS NOT NULL` | Pre-check idempotente al enqueue |
| `idx_notifications_dead_letter` | `(created_at) WHERE status='DEAD_LETTER'` | Triage admin del backlog (oldest first) |

**Trigger** `set_updated_at()` reutilizado de V2.

**Permission catalog extendido** (3 perms en dominio `NOTIFICATIONS` icono `i-lucide-bell` order 120):

- `NOTIFICATION_VIEW_ALL` — admin ve toda la cola y el historial.
- `NOTIFICATION_RESEND` — admin reintenta manualmente una notificación FAILED/DEAD_LETTER.
- `NOTIFICATION_VIEW_OWN` — usuario ve sus propias notificaciones (futuro `GET /v1/me/notifications`).

**Distribución de grants** (mirror exacto de `REFERRAL_CODE_VIEW_OWN` per V6):

- `SYSTEM` + `ADMINISTRADOR` → todos los 3 perms (VIEW_ALL + RESEND + VIEW_OWN).
- `AFILIADO` + `PROMOTOR` + `ALIADO` → solo VIEW_OWN.
- `OPERADOR*` → excluidos (actúan en nombre de otros, no reciben notificaciones personales).

### `audit_log`

(pendiente)

### Vista `digital_cards_v`

(pendiente)

## Índices clave

| Tabla | Índice | Justificación |
|---|---|---|
| `members` | `(document_number)` UNIQUE | Búsqueda por cédula en validador |
| `members` | GIN `(full_name) WITH unaccent` | Búsqueda full-text en español |
| `memberships` | `(status, next_due_date)` | Job diario de solvencia |
| `memberships` | `(member_id, status)` | Mostrar membresía activa por afiliado |
| `payments` | `(status, payment_date DESC)` | Cola de pagos pendientes |
| `payments` | `(membership_id, payment_date DESC)` | Historial por membresía |
| `benefit_usages` | `(membership_id, usage_date DESC)` | Historial uso por afiliado |
| `benefit_usages` | `(ally_id, usage_date DESC)` | Métricas por aliado |
| `commissions` | `(promoter_id, status, cycle_end)` | Liquidación |
| `audit_log` | `(entity, entity_id, timestamp DESC)` | Trace por entidad |
| `audit_log` | `(user_id, timestamp DESC)` | Auditoría por usuario |

## Performance tuning planeado

- `postgresql.conf`: `shared_buffers=3GB`, `effective_cache_size=9GB`, `work_mem=32MB`, `maintenance_work_mem=512MB`
- Vistas materializadas para reportes pesados (refresh cada 1h)
- Particionamiento futuro: `benefit_usages` por mes, `payments` por año

## Referencias

- [ADR 0005 Flyway + JPA validate](../../../centro-optico-vicente/.ai/decisions/0005-flyway-jpa.md)
- [ADR 0006 Table conventions](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md)
- [hub `05-domain-model.md`](../../../centro-optico-vicente/.ai/specs/05-domain-model.md)
- [`../playbooks/new-migration.md`](../playbooks/new-migration.md)
