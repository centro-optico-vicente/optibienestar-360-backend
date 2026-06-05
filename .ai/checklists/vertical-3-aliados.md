# Alcance 3 — Aliados

> Directorio de clínicas, farmacias, ambulancias. Requerido antes del Validador.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V11__allies.sql` — allies, ally_specialties, ally_services, ally_agreements
- [ ] [P0/C3] `V12__ally_users.sql`

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

- [ ] [v2] [P0/C2] `ally_services` ampliada: columna `status` ENUM ('PROPOSED','IN_REVIEW','APPROVED','REJECTED') default 'PROPOSED', `reviewed_by` FK users, `reviewed_at`, `rejection_reason` TEXT, `discount_pct` numeric — el aliado propone, admin aprueba antes de publicar.
- [ ] [v2] [P0/C2] `ally_service_review_log` — historial de cambios de estado (`from_status`, `to_status`, `actor`, `at`, `comment`). Visible para admin Y para el aliado dueño del servicio.

### Endpoints

- [ ] [v2] [P0/C2] `POST /v1/aliado/services` — el aliado propone un servicio + porcentaje de descuento (entra en `PROPOSED`).
- [ ] [v2] [P0/C2] `GET /v1/admin/ally-services/pending` — cola de revisión (filtrable por aliado, tipo).
- [ ] [v2] [P0/C2] `POST /v1/admin/ally-services/{uuid}/approve` (requiere `ALLY_SERVICE_APPROVE`).
- [ ] [v2] [P0/C2] `POST /v1/admin/ally-services/{uuid}/reject` (requiere `ALLY_SERVICE_APPROVE`, exige `reason`).
- [ ] [v2] [P0/C2] `GET /v1/aliado/services/{uuid}/log` y `GET /v1/admin/ally-services/{uuid}/log` — mismo endpoint con scope diferente, log compartido.
- [ ] [v2] [P0/C1] `GET /v1/public/allies/{id}` solo expone `ally_services` con `status='APPROVED'`.

### Permisos (V6 ampliación o nueva migración)

- [ ] [v2] [P0/C1] Permiso `ALLY_SERVICE_APPROVE` (asignado a ADMINISTRADOR + SYSTEM).
