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
| `V3__app_roles.sql` | DB roles `optisalud_migration`/`optisalud_app`/`optisalud_readonly` con CONNECTION LIMIT + statement_timeout + idle_in_transaction_timeout; REVOKE PUBLIC; ALTER ROLE search_path |
| `V4__contact_messages.sql` | contact_messages (landing) |
| `V5__users_and_roles.sql` | security_policies, roles, **permission_domains** (10 seed), permissions (con `domain_id` FK), users, user_password_history, user_roles, role_permissions, user_sessions_log |
| `V6__seed_roles.sql` | 7 roles + 50 permisos (incluye `ROLE_PERMISSION_EDIT`) + asignación rol→permisos + security_policies default |
| `V7__seed_users.sql` | 2 usuarios seed (System, Administrador) |
| `V8__locations.sql` | countries, states, cities (seed Venezuela 24 estados + capitales) |
| `V9__personal_catalogs.sql` | genders, document_types, marital_statuses, occupations |
| `V10__health_catalogs.sql` | medical_specialties, service_categories, ally_types |

### Planeadas

> 📌 **Renumeración del 2026-06:** se insertaron `V15__persons.sql` y `V16__refactor_users_persons.sql` (persons hub central + extracción de demográficos de users). Todo lo que era V15+ se bumpeó +2. Ver [ADR 0011](../decisions/0011-persons-identity-hub.md).

| Migration | Tablas/cambios | Vertical |
|---|---|---|
| `V11__allies.sql` | allies, ally_specialties, ally_services, ally_agreements | 3 — aliados |
| `V12__ally_users.sql` | ally_users | 3 — aliados |
| `V13__plans.sql` | plans (+ seed plan personal base) | 5 — planes |
| `V14__seed_plans.sql` | seed planes Individual / Familiar / Corporativo | 5 — planes |
| `V15__persons.sql` | **persons** — identity hub (nombres partidos, doc, RIF, contacto, address, city_id FK) | cross-cutting (ADR 0011) |
| `V16__refactor_users_persons.sql` | backfill persons desde users + ALTER users (drop demográficos, add `person_id` FK NOT NULL UNIQUE) | cross-cutting (ADR 0011) |
| `V17__members.sql` | members (FK person_id), member_documents | 4 — afiliados |
| `V18__beneficiaries.sql` | beneficiaries (FK person_id) | 4 — afiliados |
| `V19__medical_records.sql` | medical_records | 4 — afiliados |
| `V20__memberships.sql` | memberships | 5 — planes |
| `V21__payments.sql` | payments | 6 — pagos |
| `V22__benefit_usages.sql` | benefit_usages | 7 — validador |
| `V23__promoters.sql` | promoters | 8 — promotores |
| `V24__commissions.sql` | commissions | 8 — promotores |
| `V25__referrals.sql` | referrals | 8 — promotores |
| `V26__notifications.sql` | notifications | 9 — notificaciones |
| `V27__digital_cards_view.sql` | vista digital_cards_v | 9 — notificaciones |
| `V28__audit_log.sql` | tabla audit_log | 10 — hardening |
| `V29__indexes_optimization.sql` | índices adicionales según EXPLAIN | 10 — hardening |

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

(pendiente)

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
