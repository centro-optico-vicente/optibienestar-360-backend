# 12 — Comisiones de promotores + referidos

> Reglas de negocio en [hub `business-rules.md` sección "Comisiones"](../../../centro-optico-vicente/.ai/context/business-rules.md).
>
> **Reescrito parcial 2026-10-09** (ver [auditoría 2026-09-23](../../../centro-optico-vicente/.ai/notes/2026-09-23_audit.md)): este documento era enteramente ficticio — `Promoter.commissionRules` (JSONB), `Commission.payment` como UUID directo, `PayoutReportDTO.executePayout()`, y el `Referral`/`ReferralService` de abajo no existen así en el código. Se reescribió a fondo **la parte de pago/liquidación** (verificada contra `CommissionPayoutService.java`, 590 líneas, y `Commission.java`), que es la que integra con el modelo unificado de pagos (ADR 0023). **No se reescribió** el motor de tiers/jerarquía/bonos completo (`commission_tiers` V42, jerarquía de promotores V101-103, bonos V37, reglas competitivas ADR 0016) — eso es un sistema real y mucho más grande que merece su propia sesión de verificación dedicada, no una pasada rápida. La sección "Referrals" de abajo tampoco se verificó — el hub ADR 0013 ya documenta que el motor real usa `referrals` (V27) → `subsidies` (V41), no el `Referral`/`ReferralService` inventados que seguían acá.

## Modelo real de `Commission` (verificado, `Commission.java`)

Tabla `commissions` (V26) — "earnings ledger": una fila por evento de comisión generado por un pago. Campos reales:

- `promoter` (FK `Promoter`), `payment` (FK `Payment`, real entidad no UUID suelto), `member` (FK denormalizada desde `payment.membership.member` para no tener que hacer join cada vez).
- `amount` + `currency`.
- `commissionPct` XOR `flatAmount` (exactamente uno, CHECK `chk_commissions_pct_xor_flat`) — snapshot del cálculo al momento de generarse, no recalculado después si cambian las reglas vigentes.
- `commissionTierId` es un `Long` suelto (sin FK formal todavía, según el propio comentario del código) — no se verificó el detalle de `commission_tiers` en esta pasada.
- **Estados reales** (`CommissionStatus`): `PENDING`, `APPROVED`, `REJECTED`, `PAID`, `VOIDED`, `DISPUTED` — 6 valores, no los 3 (`PENDING`/`PAID`/`CANCELLED`) que decía este documento antes.

**No existe** `Promoter.commissionRules` (JSONB) ni el `type: PERSONAL/EMPRESARIAL/UNIVERSITARIO/COMUNITARIO` de este documento — el tipo de promotor es un catálogo de BD (`promoter_types`, V43), no un enum fijo con reglas embebidas; ver `domain-glossary.md` del hub para la corrección de taxonomía de planes y comisión.

## Gate de aprobación comercial (verificado, `CommissionPayoutService` Javadoc)

Antes de pagar, **gerencia comercial** debe aprobar — administración solo puede desembolsar lo ya aprobado:

- Las `Commission` deben estar `APPROVED` (una `PENDING` o `REJECTED` nunca entra al payout).
- Los `PromoterHierarchyOverride` no tienen estado de aprobación propio — heredan: un override solo es pagable si la `Commission` raíz que lo financia (subiendo la cadena `source_override_id` hasta `source_commission_id`) ya está `APPROVED`.
- Los `CommissionRetroactiveTopUp` no necesitan gate propio — solo se calculan a partir de comisiones/overrides ya `PAID`, así que la aprobación ya quedó satisfecha transitivamente.
- El re-rating (ajuste de tier) corre **antes** de la revisión comercial, nunca después de `APPROVED` — para no cambiar en silencio un número que alguien ya firmó.

## `CommissionPayoutService` — cierre de período real

Dos operaciones reales, ambas en `AdminCommissionController` (base `/v1/admin/commissions`, permiso `COMMISSION_PAYOUT`):

| Método | Endpoint | Qué hace |
|---|---|---|
| `execute(CommissionPayoutRequest, actorUuid)` | `POST /payout` | Cierra un **rango de fechas**: toma todas las `Commission` `APPROVED` + overrides/top-ups pagables del período, agrupa por promotor, marca todo `PAID`, genera CSV por promotor, envía email. |
| `executeBySelection(CommissionPayoutBySelectionRequest, actorUuid)` | `POST /payout/by-selection` | Paga un **conjunto puntual** de `Commission` elegidas a mano desde la tabla de aprobación — rechaza la request entera (400) si alguna no está `APPROVED` (todo-o-nada, nunca "pagué algunas"). |

`CommissionPayoutRequest`: `periodStart`, `periodEnd`, `payoutReference` (obligatorio — referencia bancaria/Zelle que ata el pago a lo que se movió fuera del sistema), `dryRun` (preview sin escribir nada), `paymentMethod` (código de `payment_methods`, default `"OTHER"` si se omite).

**Pagos reales `OUT` generados (V117-V120, no un simple cambio de estado):** por cada promotor, hasta 3 headers `Payment` con `direction=OUT` — uno por `payment_category` (`COMMISSION_REGULAR`, `HIERARCHY_OVERRIDE`, `RETROACTIVE_TOPUP`), porque `payments.payment_type_id` es un solo `PaymentCategory` por header y un batch puede mezclar conceptos. Cada uno con una `PaymentLine` usando el método pedido. El vínculo de vuelta a la comisión es la FK `payoutPayment` (V118); `payoutReference` (texto libre) se mantiene en paralelo, no reemplazado.

**Caso especial `INSTITUCION`:** el promotor-sistema `INSTITUCION` no tiene `Promoter.person` (nullable, V25) — pero `payments.person_id` es `NOT NULL`, así que **no se le puede crear un `Payment` de payout**. Sus filas igual se marcan `PAID` vía `payoutReference`, pero `payoutPayment` queda `null` — el único caso donde el vínculo real es estructuralmente imposible, no solo no implementado.

**Trade-off conocido de moneda (v1):** el servicio agrupa y suma por promotor asumiendo una sola moneda por batch — un batch mixto (algunas comisiones en USD, otras en VES para el mismo promotor) se suma bajo la primera moneda que aparece. Aceptado porque v1 solo produce comisiones en USD (default V26); si llega multi-moneda en v2, hay que partir por (promotor, moneda).

**Fallos de email no revierten el pago**: los `UPDATE` en BD se confirman antes de mandar los correos; si el email falla, la fila queda igual `PAID`, el fallo se loguea y se refleja en la respuesta (`emailFailure`) — admin puede reenviar a mano.

## Lo que NO se verificó en esta pasada (pendiente de sesión dedicada)

- El motor completo de `commission_tiers` (V42, bandas por monto), `commission_bonus_rules` (V37, bonos por escala), jerarquía de promotores (`PromoterHierarchyOverride`, V101-103) y reglas competitivas (ADR 0016) — existen y están en uso (confirmado indirectamente por el gate de aprobación de arriba), pero su lógica de cálculo exacta no se leyó línea por línea en esta sesión.
- `HierarchyOverrideService.cascadeFrom()` — se sabe que existe y que lo dispara `PaymentsService.attributeCommission()` al aprobar un pago `IN`, pero su algoritmo de cascada no se detalla acá.
- La sección "Referrals" original de este documento (`Referral`/`ReferralService`) — el hub [ADR 0013](../../../centro-optico-vicente/.ai/decisions/0013-incentives-engine-v3.md) ya documenta que el motor real es `referrals` (V27) → `subsidies` (V41), pero la implementación detallada no se verificó acá; se elimina el modelo inventado en vez de dejarlo como si fuera real.

## Referencias

- [hub `business-rules.md`](../../../centro-optico-vicente/.ai/context/business-rules.md)
- [hub ADR 0013 — Motor de incentivos v3](../../../centro-optico-vicente/.ai/decisions/0013-incentives-engine-v3.md)
- [hub ADR 0016 — Reglas de comisión competitivas](../../../centro-optico-vicente/.ai/decisions/0016-competitive-commission-rules.md)
- [hub ADR 0017 — Base de comisión neta](../../../centro-optico-vicente/.ai/decisions/0017-net-commission-base-and-portfolio-reassignment.md)
- [hub ADR 0023 — Modelo unificado de pagos](../../../centro-optico-vicente/.ai/decisions/0023-unified-payments-model-reality.md)
- [11-billing-manual.md](11-billing-manual.md) — cuándo se crea la comisión (`PaymentsService.attributeCommission`)
