# Scope Additions v2 — Adicionales surgidos post-arranque

> **Fuente formal:** PDF "Informe de Avances y Solicitud de Continuidad v1 — COMPLETO" (mesas técnicas equipo administrativo, mayo–junio 2026).
> **Fuente auxiliar:** flyer comercial "OPTIBIENESTAR 360 — Centro Óptico Vicente amplía sus servicios en planes" (jun 2026) — precios y SKUs de planes.
>
> Este doc es el **mapa central de trazabilidad** de los adicionales v2. Cada ítem PDF está mapeado a su vertical de implementación. Bullets `[v2]` en los checklists referencian este doc.

## Numeración

- **v1** — alcance original aprobado al inicio del proyecto (los bullets sin tag `[vN]` en cada checklist).
- **v2** — adicionales actuales (este documento). Bullets marcados `[v2]` en cada vertical.
- **v3** — reservado para futuras mesas técnicas. Cuando surjan, crear `scope-additions-v3.md` y usar tag `[v3]`.

## Mapeo: ítem PDF → vertical de implementación

| # | Ítem PDF | Vertical (dónde vive) | Modo de cambio | Estado |
|---|---|---|---|---|
| 1 | Gestión de Subsidios y Exoneraciones | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | 🆕 **nuevo vertical** | Decidido: permisos `SUBSIDY_APPROVE` + `ALLOWS_DISCOUNT`; admin autoriza |
| 2 | Manejo de Descuentos por Referidos | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende | Decidido: metas configurables + estrategia `EXPIRES` o `ACCUMULATES`; tipo de beneficio elegido al canjear |
| 3 | Planes Corporativos y Masivos | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 🔄 extiende | Parcial: `Plan.type` enum + `corporate_contracts` + `payer_mode`. **TBD:** monto exacto del corporativo + payer_mode default |
| 4 | Módulo de Promotores y Asesores | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende | Decidido: dashboard `/v1/promoter/me` + `referral_code` único por promotor |
| 5 | Motor de Comisiones + Premiaciones | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende | Decidido: `commission_tiers` + leaderboard top 3; `period_strategy` por tier (DAILY..ANNUAL); sin promotor = híbrido (system promoter INSTITUCION + `promoter_id` nullable con late-assignment) |
| 6 | Flujo de Aprobación Aliados | [vertical-3](checklists/vertical-3-aliados.md) | 🔄 extiende | Decidido: estados PROPOSED→IN_REVIEW→APPROVED/REJECTED + `ally_service_review_log` compartido admin+aliado |
| 7 | Inclusión/Modificación de Beneficiarios | [vertical-4](checklists/vertical-4-afiliados-y-familia.md) + [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 🔄 extiende | Decidido: tope `plan.max_beneficiaries` parametrizable; mensualidad fija; **cada beneficiario extra paga su inscripción one-time** ($5 según flyer) |

## Decisiones tomadas (jun 2026)

### Item #1 — Subsidios y Exoneraciones
- Permisos dedicados `SUBSIDY_APPROVE` (subsidios recurrentes) y `ALLOWS_DISCOUNT` (descuentos puntuales sobre un payment).
- Solo administrador por ahora — no se separa en sub-permisos CREATE/REVOKE/MODIFY.
- Soporte tanto a exoneración 100% como subsidio parcial X% — la columna `percentage` lo permite.
- Audit log inmutable obligatorio (`subsidy_audit_log`) por requerimiento explícito del PDF.

### Item #2 — Descuentos por Referidos
- Meta configurable por programa (no hardcoded a 10).
- Estrategia de expiración configurable por programa: `EXPIRES` (caduca si no se canjea) o `ACCUMULATES` (sigue sumando para premios mayores, modelo de puntos).
- Tipo de beneficio (`DISCOUNT_PCT`, `EXONERATION_MONTH`, `FREE_CONSULTATION`, ...) se elige al momento del canje, no al alcanzar la meta.

### Item #3 — Planes Corporativos
- Tipos de plan: `INDIVIDUAL`, `FAMILIAR`, `CORPORATIVO` (enum en `Plan.type`).
- Inscripción del corporativo puede ser menor que individual; mensualidad se mantiene en $5 (alineado con flyer "Pago Mensual: $5").
- `payer_mode` ENUM: `INSTITUTION_BULK` (la institución paga por todos sus miembros en un solo cargo) o `INDIVIDUAL_PAYER` (cada miembro paga su parte).
- Tabla `corporate_contracts` separada del plan; `Member.corporate_contract_id` FK opcional.

### Item #4 — Promotores
- Cada promotor tiene un `referral_code` único; al registrar Member con ese código → asociación automática `member.promoter_id`.
- Panel propio `/v1/promoter/me` con dashboard (cobranza, comisiones, leaderboard).

### Item #5 — Comisiones Escalonadas
- Tiers configurables (umbral de usuarios → % o monto plano).
- Soporte tarifa plana alternativa al %.
- Leaderboard top 3 con premios automáticos al cierre del período.
- **Período por tier configurable** entre `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `QUARTERLY`, `SEMIANNUAL`, `ANNUAL`. Cada tier puede tener su propio grano de período. Todos son períodos calendario (lunes-domingo para WEEKLY, mes calendario para MONTHLY, etc.) — no usamos ventanas móviles para mantener reportes alineables con contabilidad.
- **"Sin promotor" — modelo híbrido decidido:**
    - Se siembra un `Promoter` de sistema `INSTITUCION` (rol GERENCIA). Las ventas atribuidas explícitamente a este promotor generan comisión que se acumula "para la gerencia/institución". `Promoter.is_system=true` distingue del resto (no aparece en leaderboard público).
    - `Member.promoter_id` es **nullable** — cuando una venta entra sin nadie asignado, no se crea fila de comisión.
    - Soporte **late-assignment** (`POST /v1/admin/members/{uuid}/assign-promoter`): cuando exista personal de atención al cliente en el futuro, se podrá asignar un promotor a un member sin promotor para seguimientos futuros (no genera comisión retroactiva, solo afecta mensualidades nuevas).

### Item #6 — Aprobación Aliados
- Workflow `ally_services.status`: PROPOSED → IN_REVIEW → APPROVED/REJECTED.
- Permiso nuevo `ALLY_SERVICE_APPROVE`.
- Log de cambios visible tanto para admin como para el aliado dueño (no es interno-solo).
- `GET /v1/public/allies/{id}` solo expone servicios `APPROVED`.

### Item #7 — Beneficiarios Modificables
- Tope `plan.max_beneficiaries` parametrizable por plan.
- "Incluidos sin cargo" = `plan.included_beneficiaries` (ej. Familiar=3); más allá, cobra inscripción extra.
- **Mensualidad del titular NO cambia al agregar/quitar beneficiarios.**
- Cada beneficiario extra paga inscripción one-time ($5 default según flyer).
- Eliminar/sustituir beneficiario NO reembolsa la inscripción ni recalcula la mensualidad.

## Decisiones pendientes (TBD) — capturar con cliente

| TBD | Vertical | Pregunta |
|---|---|---|
| Plan Familiar — max beneficiaries | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | El flyer no aclara el tope familiar — ¿son 3 incluidos + 2 extra ($25 inscripción total)? ¿O tope duro 5? |
| Plan Corporativo — "$5/persona" | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | ¿$5 es solo mensualidad, solo inscripción, o ambas? |
| Plan Corporativo — `payer_mode` default | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | ¿Se decide por contrato (sin mixto) o se permite que algunos miembros del mismo contrato paguen individual? |
| Subsidios — alcance | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | ¿Solo exoneración 100% o también parciales (X%)? La columna `percentage` ya lo soporta — pregunta para contratos comerciales |
| Subsidios — notificación | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | ¿Se notifica al titular cuando se le otorga/revoca un subsidio? |

## Cambios al checklist v1

- **vertical-4** — el límite hardcoded "máx 3 beneficiarios" pasa a ser parametrizable por plan (`plan.max_beneficiaries`). El bullet v1 fue editado con nota cruzada a esta sección.
- **vertical-5** — el seed `V14__seed_plans.sql` se amplía con Familiar y Corporativo + nuevos campos (`type`, `included_beneficiaries`, `max_beneficiaries`, `extra_beneficiary_inscription_fee`). El bullet v1 fue editado con nota cruzada.

## Cambios al índice global

- [`../checklist-vertical.md`](../checklist-vertical.md) — agregar vertical-12 a la lista.
