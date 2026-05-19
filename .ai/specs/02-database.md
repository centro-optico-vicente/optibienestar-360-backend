# 02 — Schema PostgreSQL completo

> Schema detallado de todas las tablas. Convenciones en [ADR 0006 cross-stack](../../../centro-optico-vicente/.ai/decisions/0006-table-conventions.md).
>
> **Estado:** este archivo se irá completando conforme se ejecuten las migraciones de la Tarea 5.1.

## Diagrama conceptual

Ver [hub `05-domain-model.md`](../../../centro-optico-vicente/.ai/specs/05-domain-model.md) para el diagrama ER de alto nivel.

## Catálogo de migraciones planeadas

| Migration | Tablas/cambios | Estado |
|---|---|---|
| `V1__initial_extensions.sql` | pgcrypto, unaccent, citext | pendiente |
| `V2__base_audit_function.sql` | función `set_updated_at()` | pendiente |
| `V3__contact_messages.sql` | tabla contact_messages | pendiente |
| `V10__users_and_roles.sql` | users, roles, permissions, user_roles, role_permissions | pendiente |
| `V11__seed_roles.sql` | seeds de roles + permisos base | pendiente |
| `V12__catalogs.sql` | countries, states, cities, genders, document_types, etc. | pendiente |
| `V13__health_catalogs.sql` | medical_specialties, service_categories, ally_types | pendiente |
| `V14__allies.sql` | allies, ally_specialties, ally_services, ally_agreements | pendiente |
| `V15__ally_users.sql` | ally_users | pendiente |
| `V16__plans.sql` | plans | pendiente |
| `V17__seed_plans.sql` | plan personal base | pendiente |
| `V18__members.sql` | members, member_documents | pendiente |
| `V19__beneficiaries.sql` | beneficiaries | pendiente |
| `V20__medical_records.sql` | medical_records | pendiente |
| `V21__memberships.sql` | memberships | pendiente |
| `V22__payments.sql` | payments | pendiente |
| `V23__benefit_usages.sql` | benefit_usages | pendiente |
| `V24__promoters.sql` | promoters | pendiente |
| `V25__commissions.sql` | commissions | pendiente |
| `V26__referrals.sql` | referrals | pendiente |
| `V27__notifications.sql` | notifications | pendiente |
| `V28__digital_cards_view.sql` | vista digital_cards_v | pendiente |
| `V29__audit_log.sql` | tabla audit_log | pendiente |
| `V30__indexes_optimization.sql` | índices adicionales según EXPLAIN | pendiente |

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

### `roles`, `permissions`, `user_roles`, `role_permissions`

(pendiente — al ejecutar V10/V11)

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
