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
| 1 | Gestión de Subsidios y Exoneraciones (incl. **Motor de Solvencia**) | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) + cross-ref [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 🆕 **nuevo vertical** + extensión | Decidido: permisos `SUBSIDY_APPROVE` + `ALLOWS_DISCOUNT`; admin autoriza. `MembershipStatusService` reconoce subsidio 100% como SOLVENT sin payment. |
| 2 | Manejo de Descuentos por Referidos + **Cobranza Delegada** + **Enlace Permanente** | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende | Decidido: metas configurables + estrategia `EXPIRES`/`ACCUMULATES`; tipo de beneficio elegido al canjear; tabla `promoter_member_contacts` con endpoints reminder/payment-promise; `Member.promoter_id` enlace permanente (solo cambia vía assign-promoter explícito) |
| 3 | Planes Corporativos y Masivos | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 🔄 extiende | Parcial: `Plan.type` enum + `corporate_contracts` + `payer_mode`. **TBD:** monto exacto del corporativo + payer_mode default + naming 1+/2+/3+ vs flyer |
| 4 | Módulo de Promotores y Asesores | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende | Decidido: dashboard `/v1/promoter/me` + `referral_code` único; collection-score endpoint |
| 5 | Motor de Comisiones + Premiaciones | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende | Decidido: `commission_tiers` + leaderboard top 3; `period_strategy` por tier (DAILY..ANNUAL); **atribución por defecto a INSTITUCION** (no NULL) — `Member.promoter_id` siempre tiene valor en alta operativa |
| 6 | Flujo de Aprobación Aliados | [vertical-3](checklists/vertical-3-aliados.md) | 🔄 extiende | Decidido: estados PROPOSED→IN_REVIEW→APPROVED/REJECTED + `ally_service_review_log` compartido admin+aliado |
| 7 | Inclusión/Modificación de Beneficiarios | [vertical-4](checklists/vertical-4-afiliados-y-familia.md) + [vertical-5](checklists/vertical-5-planes-y-membresias.md) | 🔄 extiende | Decidido: tope `plan.max_beneficiaries` parametrizable; mensualidad fija; **cada beneficiario extra paga su inscripción one-time** ($5 según flyer) |

## Decisiones tomadas (jun 2026)

### Item #1 — Subsidios y Exoneraciones (+ Motor de Solvencia)
- Permisos dedicados `SUBSIDY_APPROVE` (subsidios recurrentes) y `ALLOWS_DISCOUNT` (descuentos puntuales sobre un payment).
- Solo administrador por ahora — no se separa en sub-permisos CREATE/REVOKE/MODIFY.
- Soporte tanto a exoneración 100% como subsidio parcial X% — la columna `percentage` lo permite.
- Audit log inmutable obligatorio (`subsidy_audit_log`) por requerimiento explícito del PDF.
- **Motor de Solvencia ajustado (PDF #1.b):** `MembershipStatusService` reconoce subsidio 100% activo como ACTIVE/SOLVENT sin requerir fila en `payments`. Subsidio parcial sí exige payment del monto reducido. Job `@Scheduled` consulta `subsidies` antes de degradar a EXPIRED. Cambio vive en vertical-5 con cross-ref desde vertical-12.

### Item #2 — Descuentos por Referidos
- Meta configurable por programa (no hardcoded a 10).
- Estrategia de expiración configurable por programa: `EXPIRES` (caduca si no se canjea) o `ACCUMULATES` (sigue sumando para premios mayores, modelo de puntos).
- Tipo de beneficio (`DISCOUNT_PCT`, `EXONERATION_MONTH`, `FREE_CONSULTATION`, ...) se elige al momento del canje, no al alcanzar la meta.

### Item #3 — Planes Corporativos
- Tipos de plan: `INDIVIDUAL`, `FAMILIAR`, `CORPORATIVO` (enum en `Plan.type`).
- Inscripción del corporativo puede ser menor que individual; mensualidad se mantiene en $5 (alineado con flyer "Pago Mensual: $5").
- `payer_mode` ENUM: `INSTITUTION_BULK` (la institución paga por todos sus miembros en un solo cargo) o `INDIVIDUAL_PAYER` (cada miembro paga su parte).
- Tabla `corporate_contracts` separada del plan; `Member.corporate_contract_id` FK opcional.

### Item #4 — Promotores (+ Enlace Permanente + Cobranza Delegada)
- Cada promotor tiene un `referral_code` único; al registrar Member con ese código → asociación automática `member.promoter_id`.
- Panel propio `/v1/promoter/me` con dashboard (cobranza al-día vs vencida con totales + drill-down, comisiones, leaderboard).
- **Enlace Permanente Cliente-Promotor (PDF 2.a):** `Member.promoter_id` solo cambia vía `POST /v1/admin/members/{uuid}/assign-promoter` explícito con audit log. NO se sustituye automáticamente al cambiar de plan ni renovar membresía.
- **Cobranza Delegada (PDF 2.b):** tabla `promoter_member_contacts` (REMINDER, PAYMENT_PROMISE, NOTE). Endpoints `POST .../reminder` y `POST .../payment-promise`. `GET /v1/promoter/me/collection-score` devuelve % de cartera al día como auto-evaluación (base para futuro bono por cobranza diferenciado del bono por venta). Guard: el promotor solo gestiona sus propios afiliados.

### Item #5 — Comisiones Escalonadas
- Tiers configurables (umbral de usuarios → % o monto plano).
- Soporte tarifa plana alternativa al %.
- Leaderboard top 3 con premios automáticos al cierre del período.
- **Período por tier configurable** entre `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `QUARTERLY`, `SEMIANNUAL`, `ANNUAL`. Cada tier puede tener su propio grano de período. Todos son períodos calendario (lunes-domingo para WEEKLY, mes calendario para MONTHLY, etc.) — no usamos ventanas móviles para mantener reportes alineables con contabilidad.
- **Atribución por defecto a Administración (PDF #7 — endurecido):**
    - Seed `Promoter` sistema `INSTITUCION` con `is_system=true`, rol GERENCIA. Filtrado del leaderboard público; visible en reportes financieros internos.
    - El service de alta de Member SIEMPRE resuelve un promotor: si trae `referral_code` válido → ese promotor real; si no trae código o se registra en taquilla → **auto-asigna INSTITUCION** (literal del PDF: "el 100% de la ganancia es captado por la administración central"). `Member.promoter_id` no queda NULL en uso operativo.
    - La nullabilidad de schema queda solo para migraciones legacy/test data.
    - Endpoint `POST /v1/admin/members/{uuid}/assign-promoter` (late-assignment, requiere `MEMBER_ASSIGN_PROMOTER`) — caso de uso principal: **trasladar INSTITUCION → real promoter** cuando un afiliado pasa a cartera de un promotor humano. NO retroactivo sobre la inscripción ya pagada; solo afecta mensualidades nuevas. Audit log obligatorio.

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
| Naming planes "1+, 2+, 3+ (Premium)" | [vertical-5](checklists/vertical-5-planes-y-membresias.md) | El PDF menciona "1+/2+/3+ Premium" pero el flyer comercial dice Individual/Familiar/Corporativo + Adicional. ¿Son alias del mismo set? ¿Tiers ortogonales (un `Plan.tier` ENUM BASIC/PLUS/PREMIUM además de `type`)? ¿Reemplazo total del naming actual? |
| Subsidios — alcance | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | ¿Solo exoneración 100% o también parciales (X%)? La columna `percentage` ya lo soporta — pregunta para contratos comerciales |
| Subsidios — notificación | [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | ¿Se notifica al titular cuando se le otorga/revoca un subsidio? |

## Cambios al checklist v1

- **vertical-4** — el límite hardcoded "máx 3 beneficiarios" pasa a ser parametrizable por plan (`plan.max_beneficiaries`). El bullet v1 fue editado con nota cruzada a esta sección.
- **vertical-5** — el seed `V14__seed_plans.sql` se amplía con Familiar y Corporativo + nuevos campos (`type`, `included_beneficiaries`, `max_beneficiaries`, `extra_beneficiary_inscription_fee`). El bullet v1 fue editado con nota cruzada.

## Cambios al índice global

- [`../checklist-vertical.md`](../checklist-vertical.md) — agregar vertical-12 a la lista.

## Auditoría de cobertura (jun 2026)

> Última revisión cruzada contra el resumen detallado del PDF que el cliente entregó en 7 bloques. Si en el futuro el cliente aporta otro detalle, repetir esta auditoría y actualizar la tabla.

| Bloque PDF | Sub-item | Cubierto en | Estado |
|---|---|---|---|
| 1. Subsidios e Impacto Social | a. Sistema de "Becas" y Omisiones | vertical-12 (`subsidies.percentage`) | ✅ |
| | b. Motor de Solvencia sin payment real | vertical-5 (sección "Solvencia con subsidio activo") + cross-ref desde vertical-12 | ✅ |
| 2. Promotores Avanzada | a. Enlace Permanente Cliente-Promotor | vertical-8 (bullet explícito "Enlace Permanente") | ✅ |
| | b. Cobranza Delegada | vertical-8 (sección "Gestión de Cobranza Delegada" + tabla `promoter_member_contacts`) | ✅ |
| | c. Códigos de Referidos Inteligentes | vertical-8 (`Promoter.referral_code` UNIQUE + descuentos via `referral_rewards`) | ✅ |
| | d. Ranking de Ventas (Gamificación) | vertical-8 (`GET /v1/admin/leaderboard` + `leaderboard_prizes`) | ✅ |
| 3. Comisiones Complejas | a. Desglose inscripción vs mensualidad | vertical-8 (`commission_tiers.applies_to` ENUM) | ✅ |
| | b. Bonos por Cumplimiento de Metas | vertical-8 (`threshold_count` + `commission_pct` escalable) | ✅ |
| | c. Tarifas Planas | vertical-8 (`flat_amount` alternativa al pct) | ✅ |
| 4. Planes Especiales | a. Suscripciones Corporativas/Masivas | vertical-5 (`corporate_contracts` + `Plan.type=CORPORATIVO`) | ✅ |
| | b. Planes 1+, 2+, 3+ (Premium) | vertical-5 (TBD nuevo, ver tabla TBDs) | ❓ TBD |
| 5. Autogestión Aliados | a. Panel de Carga para Aliados | vertical-3 (`POST /v1/aliado/services`) | ✅ |
| | b. Flujo de Aprobación Administrativa | vertical-3 (workflow PROPOSED→APPROVED + `ALLY_SERVICE_APPROVE`) | ✅ |
| 6. Beneficiarios Dinámica | a. Miembros Adicionales $5 USD | vertical-4 + vertical-5 (`extra_beneficiary_inscription_fee` default $5) | ✅ |
| | b. Sustitución de Beneficiarios | vertical-4 (`DELETE /v1/admin/members/{uuid}/beneficiaries/{benUuid}`) | ✅ |
| 7. Reglas Operativas | Atribución por Defecto a Administración | vertical-8 (auto-asigna INSTITUCION; `promoter_id` no queda NULL en alta operativa) | ✅ |

**Conteo:** 14 sub-items cubiertos + 1 TBD nuevo = 15/15 trazables a vertical concreto.
