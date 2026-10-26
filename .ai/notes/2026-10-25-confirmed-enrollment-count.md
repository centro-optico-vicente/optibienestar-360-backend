# Conteo de nuevos suscriptores: solo inscripciones confirmadas (2026-10-25)

> **Cambia el cálculo de comisiones.** Solicitado por el dueño del producto sabiendo que altera el conteo.
> Cierra el ítem `[v3] [P1/C2]` de [vertical-8](../checklists/vertical-8-promotores-comisiones-referidos.md) y la corrección "Confirmados" del ADR 0013 del hub.

## Qué cambió

`MemberRepository.countNewSubscribersForPromoter(promoterId, start, end)` ya no cuenta por `members.enrolled_at`. Cuenta afiliados con:

- `active = true` y `confirmed_at IS NOT NULL` (V45), y
- `confirmed_at` dentro de la ventana (`[start, end]` inclusivo, fechas locales en la zona de la app, `America/Caracas`).

Predicado único y nombrado: `MemberRepository.CONFIRMED_ENROLLMENT_PREDICATE`. Para cambiar la regla de "confirmada", se edita solo ahí.

## Qué significa "confirmada" (evidencia)

- ADR 0013 (hub): "el conteo cuenta inscripciones con **pago aprobado** en la ventana, no `Member.enrolled_at`".
- `scope-additions-v3.md`: "Conteo por inscripción confirmada (pago aprobado)".
- Código: `Member.confirmedAt` se sella en el primer pago `APPROVED` (`PaymentsService.confirmMemberOnFirstApprovedPayment`, que cubre también la auto-aprobación de métodos sin revisión) o manualmente (`MemberConfirmationService`, permiso `MEMBER_CONFIRM`) para afiliados 100% subsidiados que nunca pagan. `PENDING`/`REJECTED` no lo sellan.
- `confirmed_at` es de escritura única: el conteo de una ventana cerrada es estable (compatible con ADR 0017: lo ya generado no se recalcula).

| Estado | ¿Cuenta? |
|---|---|
| Inscripción pendiente (sin pago aprobado) | No |
| Pago rechazado | No |
| Confirmado (primer pago aprobado o confirmación manual), activo | Sí, en la ventana donde se confirmó |
| Cancelado/desactivado (`active = false`) | No |

## Orden de efectos al aprobar un pago

`PaymentsService.applyApprovalEffects` ahora confirma al afiliado **antes** de atribuir la comisión. Antes iba después; con el nuevo conteo, el propio pago de inscripción no habría contado para su tier.

## Quién cambia (todos usan el método)

| Llamador | Cambia |
|---|---|
| `CommissionService.selectTier` (banda al aprobar un pago) | Sí |
| `CommissionPeriodicSettlementService.settleCut` | Sí |
| `CommissionReRatingService` (re-rating de PENDING al cierre) | Sí |
| `CommissionRetroactiveTopUpService` (DIRECT_INSCRIPTION, 2 sitios) | Sí (solo el conteo; el `TODO(revisar)` de bandas de cobranza no se tocó) |

## Quién NO cambia (sigue por `enrolled_at`)

- `countNewSubscribersForPromoters` (equipo): `HierarchyOverrideService`, `HierarchyOverridePeriodicSettlementService`, `HierarchyOverrideReRatingService` y la rama de equipo de `CommissionRetroactiveTopUpService`.
- `countNewSubscribersByPromoter` (métricas de bonos) y `findNewSubscriberEvents` (FIRST_TO_REACH / RANKING, `NEW_SUBSCRIBERS`).

Consecuencia: el conteo directo y el de equipo/competitivo quedan con definiciones distintas hasta que el dueño decida alinearlos.

## Decisiones abiertas

1. ¿Alinear también los conteos de equipo (jerarquía), bonos y reglas competitivas con "confirmada"?
2. Sin backfill: afiliados con `confirmed_at` NULL que ya tienen pago aprobado (anteriores a V45) no cuentan. ¿Se estampa `confirmed_at` retroactivamente?
3. Ventana por fecha de **confirmación** (elegida, ADR 0013 "pago aprobado en la ventana") vs fecha de inscripción con filtro de confirmado.
