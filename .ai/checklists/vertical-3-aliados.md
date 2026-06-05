# Alcance 3 — Aliados

> Directorio de clínicas, farmacias, ambulancias. Requerido antes del Validador.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C3] `V11__allies.sql` — allies, ally_specialties, ally_services, ally_agreements _(Implementada incluyendo desde el día 1 los campos del workflow v2 sobre `ally_services` y la tabla `ally_service_review_log` — ver bullets v2 marcados abajo. También seed del permiso `ALLY_SERVICE_APPROVE` a SYSTEM + ADMINISTRADOR. Decisiones: `tax_document` (RIF) opcional con UNIQUE parcial; `manager_user_id` FK NULL para aliados gestionados centralmente; índice GIN unaccent sobre `name` para búsqueda; `ally_service_review_log` es insert-only (sin trigger updated_at, no audit columns). contextLoads aplica el chain V1..V11 limpio sobre BD vacía.)_
- [x] [P0/C3] `V12__ally_users.sql` _(Pivote N:M user↔ally con rol intra-aliado `ally_role` ENUM (OWNER/STAFF/VIEWER) + `is_primary` para el contacto principal. CHECK `is_primary → ally_role = OWNER`. Partial unique index garantiza UN solo primary activo por ally. UNIQUE (ally_id, user_id) — readmisión muta `is_active` del registro existente, no inserta duplicado. Aprovecha y dropea `allies.manager_user_id` (V11) ahora redundante — single source of truth vía `is_primary`. ON DELETE CASCADE en ally_id. contextLoads aplica V1..V12 limpio.)_

## Código

- [ ] [P0/C2] Entidades Ally, AllyType, MedicalSpecialty, AllyService, AllyAgreement, AllyUser
- [ ] [P0/C2] Repos + Services (findByDocument, searchByLocationAndSpecialty)
- [ ] [P0/C2] DTOs (Create, Update, ListItem, Detail, Agreement)
- [ ] [P0/C3] `/v1/admin/allies` CRUD + RSQL
- [ ] [P0/C2] `/v1/admin/allies/{id}/specialties|services|agreements|users`
- [ ] [P0/C2] `GET /v1/public/allies` (directorio público)
- [ ] [P0/C2] `GET /v1/public/allies/{id}`
- [ ] [P0/C2] Upload logo aliado → StorageService → R2

## Adicionales v2 — Flujo de Aprobación de Servicios

> Ver [`../scope-additions-v2.md`](../scope-additions-v2.md) (ítem PDF #6).

### Migraciones

- [x] [v2] [P0/C2] `ally_services` ampliada: columna `review_status` ENUM ('PROPOSED','IN_REVIEW','APPROVED','REJECTED','**REMOVED**') default 'PROPOSED', `reviewed_by` FK users, `reviewed_at`, `review_reason` TEXT (aplica a REJECTED y REMOVED), `discount_pct` numeric. Workflow: `PROPOSED → IN_REVIEW → APPROVED → REMOVED` con bypass `→ REJECTED` desde PROPOSED/IN_REVIEW. CHECK constraint exige `review_reason + reviewed_by` cuando el estado es REJECTED o REMOVED. _(Schema implementado en V11 — falta el código de service/endpoints para mover los estados.)_
- [x] [v2] [P0/C2] `ally_service_review_log` — tabla insert-only con `from_status`, `to_status`, `actor_user_id`, `action_at`, `comment`. Visible para admin Y para el aliado dueño (se filtra en service por `ally_service.ally.manager_user_id = currentUser`). Índice `(ally_service_id, action_at DESC)` para historial cronológico inverso. _(Schema implementado en V11 — endpoints/logging desde service code pendientes.)_

### Endpoints

- [ ] [v2] [P0/C2] `POST /v1/aliado/services` — el aliado propone un servicio + porcentaje de descuento (entra en `PROPOSED`).
- [ ] [v2] [P0/C2] `GET /v1/admin/ally-services/pending` — cola de revisión (filtrable por aliado, tipo).
- [ ] [v2] [P0/C2] `POST /v1/admin/ally-services/{uuid}/approve` (requiere `ALLY_SERVICE_APPROVE`).
- [ ] [v2] [P0/C2] `POST /v1/admin/ally-services/{uuid}/reject` (requiere `ALLY_SERVICE_APPROVE`, exige `reason`).
- [ ] [v2] [P0/C2] `POST /v1/admin/ally-services/{uuid}/remove` (requiere `ALLY_SERVICE_APPROVE`, exige `reason`; transición sólo desde APPROVED).
- [ ] [v2] [P0/C2] `DELETE /v1/aliado/services/{uuid}` (el aliado dueño retira su servicio APPROVED — internamente transiciona a REMOVED con `actor = currentUser`, no requiere `ALLY_SERVICE_APPROVE`).
- [ ] [v2] [P0/C2] `GET /v1/aliado/services/{uuid}/log` y `GET /v1/admin/ally-services/{uuid}/log` — mismo endpoint con scope diferente, log compartido.
- [ ] [v2] [P0/C1] `GET /v1/public/allies/{id}` solo expone `ally_services` con `status='APPROVED'`.

### Permisos (V6 ampliación o nueva migración)

- [x] [v2] [P0/C1] Permiso `ALLY_SERVICE_APPROVE` (asignado a ADMINISTRADOR + SYSTEM). _(Seed en V11 vía `INSERT INTO permissions` + `role_permissions` join — usa el `permission_domains.code='ALLIES'` ya existente del V5 seed. Asignado solo a SYSTEM + ADMINISTRADOR; el aliado nunca aprueba sus propios servicios.)_
