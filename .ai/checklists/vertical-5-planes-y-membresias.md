# Alcance 5 — Planes y Membresías

> Núcleo del negocio: suscripción + ciclo ACTIVE → SUSPENDED → EXPIRED.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V13__plans.sql`
- [ ] [P0/C2] `V14__seed_plans.sql` — plan personal $10 inscripción / $5 titular / $5 beneficiario
- [ ] [P0/C3] `V18__memberships.sql`
- [ ] [P0/C2] Índices: `memberships(status, next_due_date)`

## Código

- [ ] [P0/C2] Entidades Plan, Membership
- [ ] [P0/C2] `/v1/admin/plans` CRUD
- [ ] [P0/C2] `POST /v1/admin/members/{id}/memberships`
- [ ] [P0/C2] `PUT /v1/admin/memberships/{id}/cancel|reactivate`
- [ ] [P0/C3] `MembershipStatusService` (ACTIVE/SUSPENDED/EXPIRED según pagos + grace period)
- [ ] [P0/C3] Job diario `@Scheduled` — actualiza status + dispara notificación
- [ ] [P1/C2] `GET /v1/admin/memberships/dashboard` KPIs
