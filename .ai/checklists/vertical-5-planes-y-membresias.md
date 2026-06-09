# Alcance 5 — Planes y Membresías

> Núcleo del negocio: suscripción + ciclo ACTIVE → SUSPENDED → EXPIRED.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C3] `V13__plans.sql` _(Tabla `plans` con TODOS los campos v2 baked-in desde día 1 — evita ALTER chains. Columnas: `code` UNIQUE (natural key estable para lookups hardcoded, e.g. "el plan Individual") + `name` (label renombrable) + `type` CHECK ENUM (INDIVIDUAL/FAMILIAR/CORPORATIVO); pricing `inscription_fee` + `monthly_fee` NUMERIC(10,2) ≥ 0; beneficiaries v2 `included_beneficiaries` INT default 0, `max_beneficiaries` INT NULL (sin tope = corporativo), `extra_beneficiary_inscription_fee` NUMERIC NULL; `grace_period_days` INT default 7 (configurable por plan); **`is_published` + `published_at`** (patrón v11 publishing — admin readea sin exponer); audit + soft-delete. CHECK constraints: max ≥ included; fees ≥ 0; grace ≥ 0. Índices: `(type)` para filtro por tipo, parcial `(is_active) WHERE is_active AND is_published` para directorio público. Seeds del flyer (Individual $10/$5, Familiar $20/$5, Corporativo TBD) van en V14 (bullet siguiente). contextLoads V1..V13 limpio.)_
- [x] [P0/C2] `V14__seed_plans.sql` — plan personal (Individual) $10 inscripción / $5 mensualidad _(Seeds los 3 SKUs del flyer en V14: **Individual** (code='INDIVIDUAL', $10/$5, 0 beneficiarios, published=TRUE), **Familiar** (code='FAMILIAR', $20/$5, 3 incluidos, max 5, $5 extra, published=TRUE), **Corporativo** (code='CORPORATIVO', $5/$5 placeholders, sin tope, **published=FALSE** porque pricing TBD con cliente — no debe aparecer en directorio público hasta cierre). `grace_period_days=7` para los 3. `code` como natural key estable — services deben hacer lookup por code, no por UUID auto-generado. Description de Corporativo deja la nota TBD visible para que el admin la vea al previewer. contextLoads V1..V14 limpio.)_
- [x] [P0/C3] `V21__memberships.sql` _(Renumerado desde V20 — `V20__bcrypt_helper.sql` ya estaba aplicado. Schema: `member_id` FK + `plan_id` FK + lifecycle dates (`enrolled_at`, `expires_at` NULL=open-ended, `next_due_date` NOT NULL, `last_paid_through` NULL hasta primer pago) + **pricing snapshot** (`inscription_fee`/`monthly_fee`/`grace_period_days` copiados de plan al alta para inmunidad ante cambios futuros del plan) + `last_status_change_at/reason` para auditoría de la última transición. `status` usa la columna de BaseEntity con CHECK pinned a ('ACTIVE','SUSPENDED','EXPIRED','CANCELED'). **Partial UNIQUE `(member_id) WHERE is_active=TRUE`** garantiza ≤1 membresía activa por member; readmission flow = soft-delete vieja + INSERT nueva en misma transacción (orden importa, partial UNIQUE no es deferrable). Índice composite `(status, next_due_date) WHERE is_active=TRUE` para el daily job que mueve ACTIVE→SUSPENDED→EXPIRED. CHECK coherence: expires_at ≥ enrolled_at, last_paid_through ≥ enrolled_at, next_due_date ≥ enrolled_at. contextLoads V1..V21 limpio.)_
- [x] [P0/C2] Índices: `memberships(status, next_due_date)` _(Implementado como índice partial `(status, next_due_date) WHERE is_active=TRUE` — el filtro `is_active` reduce escaneo del daily job a solo membresías vivas; las soft-deleted no necesitan transitionar status.)_

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

- [x] [v2] [P0/C2] Columna `type` ENUM ('INDIVIDUAL','FAMILIAR','CORPORATIVO'). _(Incluida en V13 desde día 1 como `VARCHAR(20) NOT NULL CHECK (type IN (...))`. Sin tipo SQL ENUM porque PG ENUMs son caros de evolucionar — VARCHAR + CHECK escala mejor a futuros tipos.)_
- [x] [v2] [P0/C2] Columna `included_beneficiaries` INT (cuántos beneficiarios entran sin cargo extra). Default 0 para Individual; ej. 3 para Familiar. _(Default 0 a nivel columna; CHECK ≥ 0.)_
- [x] [v2] [P0/C2] Columna `max_beneficiaries` INT (tope duro de beneficiarios). NULL = sin tope (caso Corporativo). _(CHECK adicional `max >= included` para coherencia.)_
- [x] [v2] [P0/C2] Columna `extra_beneficiary_inscription_fee` NUMERIC (costo one-time por beneficiario adicional fuera del tope incluido). Default $5 según flyer. _(NUMERIC(10,2) nullable — `NULL` = "el plan no permite extras"; el seed V14 setea $5.00 para Familiar/Corporativo.)_

### Migraciones — `V14__seed_plans.sql` ampliado

- [x] [v2] [P0/C2] Insert Plan **Individual**: type=INDIVIDUAL, inscription=$10, monthly=$5, included_beneficiaries=0, max_beneficiaries=0, extra_fee=NULL. _(Insertado en V14 con `published=TRUE`.)_
- [x] [v2] [P0/C2] Insert Plan **Familiar**: type=FAMILIAR, inscription=$20, monthly=$5, included_beneficiaries=3, max_beneficiaries=5 (TBD si el flyer dice otro), extra_fee=$5. _(Insertado en V14 con `published=TRUE`. `max_beneficiaries=5` sigue siendo TBD — confirmar con cliente; ajuste post-seed via `/v1/admin/plans` cuando exista.)_
- [x] [v2] [P0/C3] Insert Plan **Corporativo**: type=CORPORATIVO, inscription=$5/persona (TBD), monthly=$5/persona, included_beneficiaries=0, max_beneficiaries=NULL, extra_fee=$5. **TBD pendiente: si la inscripción reducida se cobra una vez o por persona; si la mensualidad es por persona o por contrato.** _(Insertado en V14 con `published=FALSE` para que no aparezca en directorio público hasta cierre con cliente. Description deja la nota TBD visible al admin.)_

### Migraciones — Contratos Corporativos

- [ ] [v2] [P0/C3] Nueva tabla `corporate_contracts`: `uuid`, `plan_id` FK (plan CORPORATIVO), `institution_name`, `institution_tax_id`, `contact_user_id`, `payer_mode` ENUM ('INSTITUTION_BULK','INDIVIDUAL_PAYER'), `expected_member_count`, `actual_member_count`, audit fields.
- [ ] [v2] [P0/C2] `Member.corporate_contract_id` FK opcional (NULL para Individual/Familiar; valor para miembros de un contrato corporativo).
- [ ] [v2] [P0/C2] Lógica de pago: si `payer_mode='INSTITUTION_BULK'`, el `payments` se factura al contrato (no al member); si `INDIVIDUAL_PAYER`, al member como cualquier afiliado.

### Endpoints Corporativos

- [ ] [v2] [P0/C2] `POST /v1/admin/corporate-contracts` CRUD.
- [ ] [v2] [P0/C2] `POST /v1/admin/corporate-contracts/{uuid}/members` — alta de miembros en bloque (CSV o lista).
- [ ] [v2] [P0/C2] `GET /v1/admin/corporate-contracts/{uuid}/members` listar miembros del contrato.

### Solvencia con subsidio activo (gap del PDF — "Motor de Solvencia")

- [ ] [v2] [P0/C2] `MembershipStatusService` reconoce **subsidio 100% activo** como condición de `ACTIVE/SOLVENT` SIN requerir fila en `payments`. Hoy el cómputo exige pago real + grace period; el ítem PDF #1.b pide saltarse el pago real cuando hay exoneración total.
- [ ] [v2] [P0/C2] Subsidios parciales (`percentage < 100`) sí exigen `payment` del monto reducido (`plan.monthly_fee * (1 - percentage/100)`) — no quedan cubiertos por la exoneración.
- [ ] [v2] [P0/C2] Job diario `@Scheduled` consulta `subsidies` activas antes de marcar EXPIRED por falta de pago — los exonerados no caen a EXPIRED.
- [ ] [v2] [P0/C2] Audit trail: marca explícita en `membership_status_log` (o el equivalente) indicando "ACTIVE por subsidio X (uuid)" en vez de "ACTIVE por payment Y".

### Pendientes (TBD) — capturar con cliente

- [ ] [v2] [P0/C1] **TBD:** confirmar `max_beneficiaries` del plan Familiar (el flyer no lo aclara — asumimos 5 hasta verificar).
- [ ] [v2] [P0/C1] **TBD:** confirmar si "$5/persona" del Plan Corporativo es solo mensualidad, solo inscripción, o ambas.
- [ ] [v2] [P0/C1] **TBD:** confirmar si `payer_mode` se decide por contrato o se permite mixto dentro del mismo contrato.
- [ ] [v2] [P0/C1] **TBD:** naming "Planes 1+, 2+, 3+ (Premium)" mencionado en la conversación del PDF vs naming del flyer (Individual/Familiar/Corporativo). Posibles interpretaciones: (a) alias comerciales del mismo set, (b) tiers ortogonales al `type` (un `Plan.tier` ENUM BASIC/PLUS/PREMIUM además de `type`), (c) reemplazo del naming. Decidir antes de cargar el seed final.
