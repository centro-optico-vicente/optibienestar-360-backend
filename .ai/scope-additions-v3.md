# Scope Additions v3 — Motor de Incentivos (comisiones escaladas, cobranza, referidos y fidelidad)

> **Fuente formal:** esquema de incentivos entregado por el dueño del programa (mesa técnica jul 2026): tabla de comisiones escaladas + bono por escala, tabla de comisión de cobranza por días, beneficios de prosumidores por referido, beneficios de fidelidad de aliados.
> **Decisión congelada:** [ADR 0013 — Motor de Incentivos v3](https://github.com/fenix-core/centro-optico-vicente/blob/main/.ai/decisions/0013-incentives-engine-v3.md) (hub).
>
> Este doc es el **mapa central de trazabilidad** de los adicionales v3. Cada ítem del esquema está mapeado a su vertical de implementación. Bullets `[v3]` en los checklists referencian este doc. Buena parte **concreta** la visión ya reservada en [`scope-additions-v2.md`](scope-additions-v2.md) (ítems PDF #2 referidos y #5 comisiones) con los números reales del negocio.

## Numeración

- **v1** — alcance original aprobado al inicio (bullets sin tag `[vN]`).
- **v2** — adicionales de las mesas mayo–junio 2026 ([`scope-additions-v2.md`](scope-additions-v2.md), bullets `[v2]`).
- **v3** — este documento (mesa jul 2026). Bullets marcados `[v3]` en cada vertical. **Sin vertical nuevo** — todo entra en verticales existentes (decisión del dueño).
- **v4** — reservado para futuras mesas. Cuando surjan, crear `scope-additions-v4.md` y usar tag `[v4]`.

## Mapeo: ítem del esquema → vertical de implementación

| # | Ítem del esquema | Vertical (dónde vive) | Modo de cambio | Estado |
|---|---|---|---|---|
| A1 | Escala de comisión por inscripción (25/30/35% retroactiva por banda, reset mensual) | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende (re-seed + re-rating) | Diseñado (ADR 0013 §1). Reusa `commission_tiers` V42 + `selectTier`; concreta v2 #5 |
| A2 | Bono por escala en $ ($100/$100/$150/$300, acumulativo intra-mes) | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🔄 extiende (seed de reglas) | Diseñado (ADR 0013 §2). **Reusa el motor de bonos V37 sin cambios** (reglas THRESHOLD que suman) |
| B | Comisión de cobranza, % decreciente por días (35→10%) + día de corte configurable | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | 🆕 tabla nueva | Diseñado (ADR 0013 §3). Nuevo `collection_commission_tiers` + `memberships.billing_start_day` |
| C | Referido-prosumidor → saltar mensualidad (3→1 mes, 9→3, no acumulable) | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) + cross-ref [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | 🔄 extiende (puente + despertar V27) | Diseñado (ADR 0013 §4). Puente `referrals` V27 → `subsidies` V41; concreta y **supersede** v2 #2 |
| D1 | Fidelidad aliado ($100 consumo / 50 compras → voucher parametrizable) | [vertical-3](checklists/vertical-3-aliados.md) + cross-ref [vertical-7](checklists/vertical-7-validador.md) | 🆕 tablas nuevas | Diseñado (ADR 0013 §5). Extiende `benefit_usages` V24 + validador |
| D2 | Cortesía de aliado (1ª consulta gratis; o por consumo del período) | [vertical-3](checklists/vertical-3-aliados.md) + cross-ref [vertical-7](checklists/vertical-7-validador.md) | 🆕 tabla nueva | Diseñado (ADR 0013 §5). `ally_courtesy_grants` validable |

## Decisiones tomadas (jul 2026)

### Ítem A — Comisión por inscripción + bono por escala
- **% retroactivo por banda:** el % lo fija el mínimo de la banda alcanzada en el mes; al cruzar un mínimo superior, **todas** las inscripciones del mes se re-pagan a ese %. Cuadra con la tabla (60×$7.50 = $450). Las comisiones `INSCRIPTION` se recalculan al **cierre de mes**; interinamente `PENDING`, solo se re-ratea `PENDING` (no `PAID`).
- **Bono acumulativo intra-mes, reset mensual:** $100 (al 41) + $100 (al 61) + $150 (al 76) + $300 (al 101) → 120 inscripciones = $650. "No acumulable" = no se arrastra al mes siguiente, **no** que las bandas no sumen. El motor de bonos V37 (reglas THRESHOLD) ya lo hace.
- **Conteo por inscripción confirmada** (pago aprobado), no por `enrolled_at`.
- Base de cálculo = inscripción real ($25–$35 según beneficiarios, ya en `BeneficiariesService`).

### Ítem B — Comisión de cobranza por días
- Tabla configurable `collection_commission_tiers` (`max_days` → `commission_pct`); gana el bucket de menor `max_days ≥` los días reales. Seed 5/10/15/20/25/30+ → 35/30/25/20/15/10%.
- **Día de corte configurable** `memberships.billing_start_day` (1–28, opcional; default = día de inscripción) — "inscrito el 3, cobro desde el 1".
- `días = max(0, payment.paymentDate − fecha_cobro_programada)`; la ruta MONTHLY de `CommissionService` usa esta tabla; monto = `pct × monthlyFee`.

### Ítem C — Referido-prosumidor → subsidio
- El "saltar mensualidad" se materializa como **subsidio `monthly_percentage=100`** (motor V41; `MembershipStatusService` ya lo honra sin `payment`).
- **Despertar** el motor de referidos V27 (hoy `registerOnEnrollment`/`applyRewardsTo` nunca se llaman) + auto-emitir `referral_code` al alta.
- `referral_reward_programs` PER_BLOCK: `goal_count=3`, `months_per_block=1`, `expiration_strategy=EXPIRES`, `period_strategy=MONTHLY` ⇒ 9 referidos = 3 meses; **no acumulable** (conteo resetea por mes).
- Evaluador `ReferralRewardJobRunner` (cron mensual) auto-otorga el subsidio; idempotente por `(member, período)`; notifica al afiliado.
- **Supersede** el reward v2 "10% off 1 pago FIFO" (deprecado o modo alterno del programa).

### Ítem D — Fidelidad y cortesías de aliados
- **Voucher canjeable, premio parametrizable** (decisión del dueño): al cruzar $100 de consumo o 50 compras en el período se emite un voucher; el canje se registra en `benefit_usages` y lo reconoce el validador.
- Extender `benefit_usages` con `consumption_amount`/`consumption_currency` (hoy solo `copay_amount`) + agregaciones SUM/COUNT por período.
- `ally_loyalty_programs` (metric CONSUMPTION_AMOUNT/PURCHASE_COUNT, threshold, reward parametrizable) + ledger `loyalty_vouchers` (PER_BLOCK, patrón V37).
- **Cortesía validable** `ally_courtesy_grants`: el aliado regala a 1 persona de una membresía (validador la reconoce una vez); también emitible automáticamente por umbral de consumo del período.

## Decisiones pendientes (TBD) — capturar con cliente

| TBD | Vertical | Pregunta | Default propuesto |
|---|---|---|---|
| Tope de meses de subsidio por referido | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) + [vertical-12](checklists/vertical-12-subsidios-y-exoneraciones.md) | ¿Hay máximo de meses ganables por referidos en un período? | Sin tope, configurable en `referral_reward_programs` |
| Caducidad del voucher de fidelidad | [vertical-3](checklists/vertical-3-aliados.md) | ¿El voucher canjeable expira? | Caduca al cierre del período siguiente (configurable) |
| Alcance de la cortesía | [vertical-3](checklists/vertical-3-aliados.md) | ¿La cortesía es para el titular o cualquier beneficiario? | 1 persona de la membresía, a elección del aliado |
| Re-liquidación retroactiva | [vertical-8](checklists/vertical-8-promotores-comisiones-referidos.md) | El % retroactivo, ¿re-liquida comisiones ya `PAID` del mes o solo `PENDING`? | Solo `PENDING` |
| Permisos v3 | [vertical-3](checklists/vertical-3-aliados.md) | Dominio/nombres exactos (`ALLY_LOYALTY_MANAGE`/`ALLY_COURTESY_GRANT`/`LOYALTY_VIEW_OWN`) | Patrón dominio existente V22/V28 |

## Cambios al checklist v2

- **vertical-8** — el ítem `[v2]` `referral_programs`/`referral_rewards` (Ítem PDF #2, dormido) queda **superseded** por el bloque `[v3]` Ítem C: el reward "10% off 1 pago FIFO" se deprecia a favor del salto de mensualidad (subsidio). Nota cruzada añadida bajo ese encabezado v2.
- **vertical-8** — la escala de comisión `[v2]` (Ítem PDF #5, tiers genéricos) se **concreta** con los números del negocio (25/30/35 + bono $100/$150/$300) en el bloque `[v3]` Ítem A.

## Cambios al índice global

- [`checklist.md`](checklist.md) — recontados verticales 3/7/8/12 con los ítems `[v3]`; nota de v3 en la sección de adicionales (los ítems viven embebidos en verticales existentes, sin fila nueva).

## Auditoría de cobertura (jul 2026)

| Bloque del esquema | Sub-item | Cubierto en | Estado |
|---|---|---|---|
| Comisiones asesores | Escala de % por inscripción (retroactiva) | vertical-8 A1 (`commission_tiers` re-seed + re-rating) | ✅ trazable |
| | Bono por escala $ (acumulativo) | vertical-8 A2 (motor de bonos V37, 4 reglas THRESHOLD) | ✅ trazable |
| | Conteo por inscripción confirmada | vertical-8 A1 (fix `countNewSubscribersForPromoter`) | ✅ trazable |
| Comisión de cobranza | % decreciente por días | vertical-8 B (`collection_commission_tiers`) | ✅ trazable |
| | Día de corte configurable | vertical-8 B (`memberships.billing_start_day`) | ✅ trazable |
| Prosumidores/referidos | Saltar mensualidad (3→1, 9→3, no acumulable) | vertical-8 C + vertical-12 (subsidio auto-otorgado V41) | ✅ trazable |
| | Despertar motor de referidos V27 | vertical-8 C (cablear `registerOnEnrollment` + auto-code) | ✅ trazable |
| Fidelidad aliados | $100 consumo / 50 compras → voucher | vertical-3 D1 (`ally_loyalty_programs` + `loyalty_vouchers`) | ✅ trazable |
| | Snapshot de monto de consumo | vertical-3 D1 (`benefit_usages.consumption_amount`) | ✅ trazable |
| | Canje reconocido por validador | vertical-7 (validador honra voucher/cortesía) | ✅ trazable |
| Regalo aliado | Cortesía (1ª consulta gratis; o por consumo) | vertical-3 D2 (`ally_courtesy_grants`) + vertical-7 | ✅ trazable |

**Conteo:** 10 sub-items del esquema, todos trazables a vertical concreto. 5 TBD abiertos (tabla arriba).
