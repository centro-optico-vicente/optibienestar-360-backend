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

### Planeadas

> 📌 **Renumeración del 2026-06:**
> - 1ª: se insertaron `V15__persons.sql` y `V16__refactor_users_persons.sql` (persons hub central + extracción de demográficos de users). Todo lo que era V15+ se bumpeó +2. Ver [ADR 0011](../decisions/0011-persons-identity-hub.md).
> - 2ª: se insertó `V20__bcrypt_helper.sql` (utilidad para seeds). Todo lo que era V20+ planeado se bumpeó +1. memberships pasó de V20 a V21.
> - 3ª: se insertó `V22__scheduled_jobs.sql` (framework de tareas programadas — cron config + audit ledger + permisos `JOB_*`). Todo lo que era V22+ planeado se bumpeó +1. payments pasó de V22 a V23.

| Migration | Tablas/cambios | Vertical |
|---|---|---|
| `V27__referrals.sql` | referrals | 8 — promotores |
| `V28__notifications.sql` | notifications | 9 — notificaciones |
| `V29__digital_cards_view.sql` | vista digital_cards_v | 9 — notificaciones |
| `V30__audit_log.sql` | tabla audit_log | 10 — hardening |
| `V31__indexes_optimization.sql` | índices adicionales según EXPLAIN | 10 — hardening |

## Decisiones diferidas y pendientes de análisis

> **Patrón general del proyecto** (aplicable a toda migración / schema work, no solo a una vertical específica): cada vez que una migración deja un **FK diferido**, un **trigger no implementado**, una **columna `_id` sin la tabla destino**, una **política de snapshot vs live** a confirmar, o una **decisión de diseño** que requiere más data productiva para validarse — se captura acá, agrupado por la migración que originó la deuda. Se revisan cuando hay ventana de análisis; **no bloquean el flujo principal**.
>
> El bullet ideal es **resolvible**: tiene un trigger claro de cierre (*"cuando V27 traiga `commission_tiers`..."*) o una decisión explícita a tomar (*"DB trigger vs service code"*). Si el item solo dice *"revisar X"* sin condición de cierre, hay que afinarlo o eliminarlo.
>
> Esta sección vive en el spec del schema porque sobrevive a refactors de las verticales (un vertical puede dividirse, fusionarse o reorganizarse — la migración + su deuda técnica no). Las verticales pueden agregar bullets a esta sección con un cross-ref desde sus checklists.

### V25 — `promoters`

- [ ] [diferido] **Snapshot counters `promoters.total_referrals` + `total_commission_paid`**: ¿se actualizan vía TRIGGER en `commissions` (más simple pero acopla schemas) o vía service code en el `CommissionService` cuando una commission pasa a `PAID` (más explícito, permite batch + reintentos)? **Trigger de cierre**: definir antes de implementar `PUT /v1/admin/commissions/{uuid}/mark-paid` o el batch de payout, ese es el momento natural en que el counter avanza.
- [ ] [diferido] **Sincronización `promoters.display_name` ↔ `persons.full_name`**: hoy se setea al crear el promotor (default `person.full_name`) pero NO se mantiene sincronizado si el `Person` se edita después. ¿Es deseado divergir (admin puede personalizar) o sincronizar al cambiar el Person? **Trigger de cierre**: surge cuando aparezca un caso real de admin que pide editar el nombre del promotor sin tocar el Person.
- [ ] [diferido] **Workflow de `promoters.status=SUSPENDED`**: el CHECK constraint lo permite (ACTIVE/INACTIVE/SUSPENDED) pero **no hay endpoint que lo aplique**. **Trigger de cierre**: agregar `PUT /v1/admin/promoters/{uuid}/suspend` con razón obligatoria cuando aparezca el caso de uso real (fraude detectado, contrato terminado). Hasta entonces, INACTIVE vía soft-delete cubre el flow.
- [ ] [diferido] **`members.promoter_id` schema NULLABLE vs service NOT NULL**: el service enforce NOT NULL al alta, pero NO hay constraint que prevenga `UPDATE members SET promoter_id = NULL` via SQL directo. ¿Agregar CHECK constraint que enforce NOT NULL cuando el row es ACTIVE/ENROLLED, o confiar en el service? **Trigger de cierre**: revisar cuando llegue el endpoint de assign-promoter — si necesita UNSET temporal, CHECK no sirve.

### V26 — `commissions`

- [ ] [diferido] **FK `commissions.commission_tier_id` → `commission_tiers`**: la columna existe como BIGINT sin REFERENCES porque la tabla `commission_tiers` no existe todavía (planeada v2). **Trigger de cierre**: cuando llegue V27+ con `commission_tiers`, agregar `ALTER TABLE commissions ADD CONSTRAINT fk_commissions_tier FOREIGN KEY (commission_tier_id) REFERENCES commission_tiers(commission_tiers_id);` al final de esa migración (mismo patrón que el FK diferido V18→V23 de `beneficiaries.inscription_payment_id`).
- [ ] [diferido] **Tabla `commission_payouts` futura vs `payout_reference VARCHAR` actual**: hoy el row de commission referencia el cobro batch via string libre. ¿Vale la pena promover a tabla `commission_payouts` (uuid, executed_at, total_amount, currency, bank_reference, ...) cuando aparezca el bullet de "cierre de período + CSV + email promotor"? Si sí, migrar `payout_reference` a `payout_id BIGINT FK`. **Trigger de cierre**: análisis al implementar `POST /v1/admin/commissions/payout`.
- [ ] [diferido] **Política de void: VOIDED row vs commission negativa**: cuando un payment se revierte/refunda, las commissions tied a él necesitan compensación. **Opción A** — VOIDED status sobre el row original (audit limpio, una sola fila por evento). **Opción B** — nueva fila con monto negativo (suma agregada = 0; mismo patrón contable que doble entrada). **Trigger de cierre**: depende de qué requiere contabilidad — definir cuando llegue el bullet del commission engine + audit con contador.
- [ ] [diferido] **Rango `commission_pct ≤ 100` (CHECK actual)**: el constraint rechaza > 100%, pero v2 menciona "premios doble pago" (ej. bono semestral 200% sobre basis). ¿Flexibilizar el CHECK o usar `flat_amount` para bonos > 100%? **Trigger de cierre**: cuando v2 `commission_tiers` introduzca el caso explícito de premio multiplicador, posiblemente bumpear el CHECK a `≤ 500` o quitarlo.
- [ ] [diferido] **Trigger automático `commissions.status=PAID` → `promoters.total_commission_paid +=`**: relacionado con el primer item de V25. **Decisión**: depende de la resolución TRIGGER-vs-service del item correspondiente de promoters.
- [ ] [diferido] **Index strategy `(promoter_id, status, period_start)` vs partial por status**: hoy el partial es solo `WHERE is_active`. Si el 99% de queries son `status='PENDING'`, podría convenir partial adicional `WHERE status='PENDING' AND is_active`. **Trigger de cierre**: cuando haya volumen real de commissions, correr `EXPLAIN ANALYZE` sobre la query de liquidation; si bitmap heap scan domina, agregar el partial.

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
