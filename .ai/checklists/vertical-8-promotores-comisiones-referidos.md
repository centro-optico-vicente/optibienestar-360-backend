# Alcance 8 — Promotores, Comisiones y Referidos

> Red de ventas: comisión automática al aprobar pago inicial + códigos de descuento.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V21__promoters.sql`
- [ ] [P0/C3] `V22__commissions.sql`
- [ ] [P0/C3] `V23__referrals.sql`

## Código

- [ ] [P0/C2] Entidades Promoter, Commission, Referral
- [ ] [P0/C3] `/v1/admin/promoters` CRUD
- [ ] [P0/C3] `CommissionService` — calcula al aprobar pago inicial (PERCENT o FLAT por tipo de plan)
- [ ] [P0/C2] `GET /v1/admin/commissions`
- [ ] [P0/C2] `POST /v1/admin/commissions/payout` — cierre de ciclo + CSV + email promotor
- [ ] [P0/C2] `GET /v1/promoter/dashboard`
- [ ] [P0/C3] `ReferralService` — valida código + aplica descuento al referrer
- [ ] [P0/C2] `POST /v1/admin/referral-codes`
- [ ] [P0/C2] `GET /v1/me/referrals`

## Adicionales v2 — Referidos, Promotores y Comisiones Escalonadas

> Ver [`../scope-additions-v2.md`](../scope-additions-v2.md) (ítems PDF #2, #4, #5).

### Ítem PDF #2 — Manejo de Descuentos por Referidos (con metas)

- [ ] [v2] [P0/C3] Tabla `referral_programs`: `uuid`, `name`, `goal_count` INT (cantidad de referidos para canjear, default 10), `expiration_strategy` ENUM ('EXPIRES','ACCUMULATES'), `expires_after_days` INT NULL (solo si EXPIRES), `active` bool. Metas configurables por programa, no hardcoded a 10.
- [ ] [v2] [P0/C3] Tabla `referral_rewards`: `uuid`, `referral_program_id` FK, `referrer_user_id` FK, `goal_reached_at`, `claimed_at` NULL, `expires_at` NULL (solo EXPIRES), `reward_type` ENUM ('DISCOUNT_PCT','EXONERATION_MONTH','FREE_CONSULTATION', otros — extensible), `reward_value`, `claimed_payment_id` FK NULL.
- [ ] [v2] [P0/C2] Si `expiration_strategy=EXPIRES`: una vez alcanzada la meta se materializa la `reward`; si no se canjea en `expires_after_days`, se marca `expired` y el contador resetea.
- [ ] [v2] [P0/C2] Si `expiration_strategy=ACCUMULATES`: el contador NO resetea — sigue acumulando para premios mayores (modelo de puntos: ej. 10 referidos = X, 20 = Y, 50 = Z).
- [ ] [v2] [P0/C2] El tipo de beneficio (`reward_type`) se elige al momento del canje, no se fija al alcanzar la meta — el referidor decide entre las opciones disponibles del programa.
- [ ] [v2] [P0/C2] `POST /v1/admin/referral-programs` CRUD.
- [ ] [v2] [P0/C2] `GET /v1/me/referral-rewards` (lista las recompensas pendientes de canjear).
- [ ] [v2] [P0/C2] `POST /v1/me/referral-rewards/{uuid}/claim` (canje del reward).

### Ítem PDF #4 — Módulo de Promotores y Asesores de Venta

- [ ] [v2] [P0/C3] Panel `/v1/promoter/me` — dashboard con: afiliados activos del promotor, cobranza al día/vencida (con totales + drill-down al afiliado individual), comisiones acumuladas del período, posición en leaderboard.
- [ ] [v2] [P0/C2] Promotor genera su `referral_code` único (formato corto, ej. 6 chars) usado para tracking de sus afiliados desde el alta.
- [ ] [v2] [P0/C2] `Promoter.referral_code` UNIQUE; al registrar un Member con `referral_code` válido, se asocia automáticamente `member.promoter_id`.
- [ ] [v2] [P0/C2] **Enlace Permanente Cliente-Promotor** (PDF 2.a): `Member.promoter_id` es enlace permanente — solo cambia vía `POST /v1/admin/members/{uuid}/assign-promoter` explícito con audit log. NO se sustituye automáticamente al cambiar de plan, renovar membresía, ni en ninguna operación del flujo regular.

#### Gestión de Cobranza Delegada (PDF 2.b)

- [ ] [v2] [P0/C2] Tabla `promoter_member_contacts`: `uuid`, `promoter_id` FK, `member_id` FK, `type` ENUM ('REMINDER','PAYMENT_PROMISE','NOTE'), `note` TEXT, `promised_amount` NUMERIC NULL, `promised_at_date` DATE NULL (solo PAYMENT_PROMISE), `created_at`. Auditoría de las gestiones que hace el promotor sobre su cartera.
- [ ] [v2] [P0/C2] `POST /v1/promoter/me/contacts/{memberUuid}/reminder` — el promotor registra que contactó al afiliado por cobranza (tipo REMINDER).
- [ ] [v2] [P0/C2] `POST /v1/promoter/me/contacts/{memberUuid}/payment-promise` — registra promesa de pago con `promised_amount` + `promised_at_date`.
- [ ] [v2] [P0/C2] `GET /v1/promoter/me/contacts?memberUuid=...` — historial de gestiones del promotor con un afiliado.
- [ ] [v2] [P0/C2] `GET /v1/promoter/me/collection-score` — % de cartera al día (auto-evaluación del promotor; base para un futuro bono por cobranza diferenciado del bono por venta).
- [ ] [v2] [P0/C2] Guard: el promotor solo puede registrar contacts sobre **sus propios afiliados** (`member.promoter_id = currentPromoter.id`); admin puede registrar para cualquier promotor.

### Ítem PDF #5 — Motor Automatizado de Comisiones y Premiaciones

- [ ] [v2] [P0/C3] Tabla `commission_tiers`: `uuid`, `name`, `threshold_count` INT (cantidad de usuarios nuevos para activar), `commission_pct` NUMERIC NULL, `flat_amount` NUMERIC NULL (uno u otro, no ambos), `period_strategy` ENUM ('DAILY','WEEKLY','BIWEEKLY','MONTHLY','QUARTERLY','SEMIANNUAL','ANNUAL'), `applies_to` ENUM ('INSCRIPTION','MONTHLY','BOTH'). Soporta tarifas planas alternativas (`commission_pct=NULL` implica `flat_amount`).
- [ ] [v2] [P0/C2] `CommissionService` consulta los tiers vigentes y aplica el más alto que el promotor califique según el `period_strategy` del tier (ej. para `MONTHLY`, contador resetea el 1° de cada mes; para `WEEKLY`, los lunes; etc.). Cada tier puede tener su propio período.
- [ ] [v2] [P0/C2] Cálculo de inicio/fin de período por enum: `DAILY`=día calendario, `WEEKLY`=lunes a domingo, `BIWEEKLY`=quincenal anclado al 1ro y 16, `MONTHLY`=mes calendario, `QUARTERLY`=trimestre calendario, `SEMIANNUAL`=semestre calendario, `ANNUAL`=año calendario. (No usamos ventanas móviles para mantener reportes alineables con contabilidad).
- [ ] [v2] [P0/C2] **Atribución por defecto a Administración** (PDF #7 endurecido): si el alta del Member no trae `referral_code` o se hace por taquilla, el `MemberService` auto-asigna `member.promoter_id = INSTITUCION` (system promoter). El 100% de la utilidad queda atribuida a la administración central — NO se omite el registro de comisión, simplemente la comisión apunta al promotor sistema. Esto matchea literal el PDF: "el 100% de la ganancia es captado por la administración central".
- [ ] [v2] [P0/C2] Seed `Promoter` de sistema `INSTITUCION`: `is_system=true`, rol GERENCIA. Su `commissions` se computan igual que cualquier promotor real (vía `commission_tiers`) pero al cierre del período el `payout` se contabiliza a favor de la institución, no se paga a un humano.
- [ ] [v2] [P0/C2] `Promoter.is_system` boolean — los promotores sistema NO aparecen en el leaderboard público de promotores reales (`leaderboard` filtra `is_system=false`), pero SÍ en reportes financieros internos del admin.
- [ ] [v2] [P0/C2] `Member.promoter_id` permanece **NOT NULL** en uso operativo — la nullabilidad de schema queda solo para migraciones legacy o test data, no para alta normal. El service falla si no logra resolver un promotor (real por código + sistema INSTITUCION como fallback siempre disponible).
- [ ] [v2] [P0/C2] `POST /v1/admin/members/{uuid}/assign-promoter` — late-assignment (requiere permiso `MEMBER_ASSIGN_PROMOTER`). Caso de uso principal: **trasladar INSTITUCION → real promoter** cuando un afiliado pasa a ser cartera de un promotor humano (ej. atención al cliente nuevo). NO genera comisión retroactiva sobre la inscripción ya pagada; solo afecta mensualidades nuevas. Audit log obligatorio (`from_promoter`, `to_promoter`, `actor`, `reason`).
- [ ] [v2] [P0/C3] Tabla `commission_period_summary` (materializada o vista): por período (según el grano del tier) y promotor, totales (count, %, monto). Base para leaderboard.
- [ ] [v2] [P0/C2] `GET /v1/admin/leaderboard?period=2026-06&strategy=MONTHLY` — top promotores reales (excluye `is_system=true`) ordenados por monto comisión (default top 3 con premio 1ro/2do/3ro), filtrable por estrategia de período.
- [ ] [v2] [P0/C2] Asignación automática de premios al top 3 al cierre del período (configurable en `leaderboard_prizes`: lugar, monto premio, period_strategy).
