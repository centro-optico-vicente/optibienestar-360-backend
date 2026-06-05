# Alcance 5 — Planes y Membresías

> Núcleo del negocio: suscripción + ciclo ACTIVE → SUSPENDED → EXPIRED.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V13__plans.sql`
- [ ] [P0/C2] `V14__seed_plans.sql` — plan personal (Individual) $10 inscripción / $5 mensualidad _(v2: extender con Familiar y Corporativo + nuevos campos, ver Adicionales v2)_
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

## Adicionales v2 — Planes Familiares, Corporativos y Tarifas Reales del Flyer

> Ver [`../scope-additions-v2.md`](../scope-additions-v2.md) (ítems PDF #3 y #7).
>
> **Fuente:** flyer comercial "OPTIBIENESTAR 360 — Centro Óptico Vicente amplía sus servicios en planes" (jun 2026): Plan Individual $10, Plan Familiar $20, Plan Corporativo $5/pers, Afiliado Adicional $5, Pago Mensual $5.

### Migraciones — `V13__plans.sql` ampliado

- [ ] [v2] [P0/C2] Columna `type` ENUM ('INDIVIDUAL','FAMILIAR','CORPORATIVO').
- [ ] [v2] [P0/C2] Columna `included_beneficiaries` INT (cuántos beneficiarios entran sin cargo extra). Default 0 para Individual; ej. 3 para Familiar.
- [ ] [v2] [P0/C2] Columna `max_beneficiaries` INT (tope duro de beneficiarios). NULL = sin tope (caso Corporativo).
- [ ] [v2] [P0/C2] Columna `extra_beneficiary_inscription_fee` NUMERIC (costo one-time por beneficiario adicional fuera del tope incluido). Default $5 según flyer.

### Migraciones — `V14__seed_plans.sql` ampliado

- [ ] [v2] [P0/C2] Insert Plan **Individual**: type=INDIVIDUAL, inscription=$10, monthly=$5, included_beneficiaries=0, max_beneficiaries=0, extra_fee=NULL.
- [ ] [v2] [P0/C2] Insert Plan **Familiar**: type=FAMILIAR, inscription=$20, monthly=$5, included_beneficiaries=3, max_beneficiaries=5 (TBD si el flyer dice otro), extra_fee=$5.
- [ ] [v2] [P0/C3] Insert Plan **Corporativo**: type=CORPORATIVO, inscription=$5/persona (TBD), monthly=$5/persona, included_beneficiaries=0, max_beneficiaries=NULL, extra_fee=$5. **TBD pendiente: si la inscripción reducida se cobra una vez o por persona; si la mensualidad es por persona o por contrato.**

### Migraciones — Contratos Corporativos

- [ ] [v2] [P0/C3] Nueva tabla `corporate_contracts`: `uuid`, `plan_id` FK (plan CORPORATIVO), `institution_name`, `institution_tax_id`, `contact_user_id`, `payer_mode` ENUM ('INSTITUTION_BULK','INDIVIDUAL_PAYER'), `expected_member_count`, `actual_member_count`, audit fields.
- [ ] [v2] [P0/C2] `Member.corporate_contract_id` FK opcional (NULL para Individual/Familiar; valor para miembros de un contrato corporativo).
- [ ] [v2] [P0/C2] Lógica de pago: si `payer_mode='INSTITUTION_BULK'`, el `payments` se factura al contrato (no al member); si `INDIVIDUAL_PAYER`, al member como cualquier afiliado.

### Endpoints Corporativos

- [ ] [v2] [P0/C2] `POST /v1/admin/corporate-contracts` CRUD.
- [ ] [v2] [P0/C2] `POST /v1/admin/corporate-contracts/{uuid}/members` — alta de miembros en bloque (CSV o lista).
- [ ] [v2] [P0/C2] `GET /v1/admin/corporate-contracts/{uuid}/members` listar miembros del contrato.

### Pendientes (TBD) — capturar con cliente

- [ ] [v2] [P0/C1] **TBD:** confirmar `max_beneficiaries` del plan Familiar (el flyer no lo aclara — asumimos 5 hasta verificar).
- [ ] [v2] [P0/C1] **TBD:** confirmar si "$5/persona" del Plan Corporativo es solo mensualidad, solo inscripción, o ambas.
- [ ] [v2] [P0/C1] **TBD:** confirmar si `payer_mode` se decide por contrato o se permite mixto dentro del mismo contrato.
