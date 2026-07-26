# Alcance 12 — Subsidios y Exoneraciones (Adicionales v2)

> 🆕 **Vertical nuevo, parte de Adicionales v2.** Ítem PDF #1 — "Gestión de Subsidios y Exoneraciones".
> Ver mapa completo en [`../scope-additions-v2.md`](../scope-additions-v2.md).
> Índice: [../checklist.md](../checklist.md)
>
> **Caso de uso (PDF):** controles para omitir o subsidiar el pago de mensualidades a perfiles especiales (fundaciones, iglesias, casos de bajos recursos) manteniendo el registro formal en auditoría.
>
> **Modelo ampliado (V41, sesión jul-2026):** más allá del boceto original, el subsidio soporta
> **2 porcentajes independientes** (`monthly_percentage` + `inscription_percentage`, 0–100 c/u,
> NULL = esa cuota no se cubre, 100 = exoneración total, X% = subsidio parcial) — así se puede
> exonerar la inscripción, la mensualidad o ambas; **`valid_until` opcional** (subsidio por tiempo
> limitado); **`max_exonerated_beneficiaries`** (tope de beneficiarios exonerados distinto al del plan);
> y una tabla hija **`subsidy_beneficiaries`** con exoneración por beneficiario (cada uno con sus 2
> porcentajes). El `percentage` único que reservaba este checklist queda **absorbido** por `monthly_percentage`.

<!-- resumen-totales:start -->
| Tareas | Hechas | Pendientes | % avance | Estado |
|---|---|---|---|---|
| 20 | 19 | 1 | 95% | 🟡 |

_Snapshot — recontar con `grep -c '^- \[x\]'`. Panorama global: [checklist.md](../checklist.md)._
<!-- resumen-totales:end -->

## Migraciones

- [x] [v2] [P0/C2] Tabla `subsidies`: `uuid`, `member_id` FK, `reason` TEXT, `authorized_by` FK users, `valid_from` DATE, `valid_until` DATE NULL (NULL = indefinido), `active` bool, audit. _(V41. **Ampliada**: `percentage` único → **`monthly_percentage` + `inscription_percentage`** NUMERIC(5,2) NULL (0–100, CHECK) + `max_exonerated_beneficiaries` INT NULL. CHECK `chk_subsidies_covers_something` (al menos una cuota cubierta) + `chk_subsidies_valid_window` (valid_until ≥ valid_from). Entidad `modules/subsidy/entity/Subsidy` con `@OneToMany` a `SubsidyBeneficiary` (cascade ALL + orphanRemoval).)_
- [x] [v2] [P0/C2] Índice `subsidies(member_id, active, valid_from, valid_until)` para resolver "¿tiene subsidio activo hoy?". _(V41: `idx_subsidies_member_window (member_id, valid_from, valid_until) WHERE is_active` — usado por `SubsidyResolver.findActiveForMember` y el sweep de solvencia.)_
- [x] [v2] [P0/C2] Tabla `subsidy_audit_log`: historial inmutable (`subsidy_id`, `action` ENUM('CREATED','REVOKED','MODIFIED'), `actor_id`, `at`, `before`, `after`, `reason`). _(V41. Append-only por convención; `before_json`/`after_json` JSONB (`@JdbcTypeCode(SqlTypes.JSON)`); `created_at` es el timestamp `at`. Entidad `SubsidyAuditLog` (enum interno `Action`).)_
  - _(**+ tabla hija nueva** `subsidy_beneficiaries` (V41): `subsidy_id` FK ON DELETE CASCADE + `beneficiary_id` FK + `monthly_percentage` + `inscription_percentage`; UNIQUE (subsidy_id, beneficiary_id) + CHECK de cobertura. Cubre "beneficiario que exonere la mensualidad o la inscripción o ambas".)_

## Permisos (V6 ampliación o nueva migración Vx)

- [x] [v2] [P0/C1] Permiso `SUBSIDY_APPROVE` — autoriza crear/revocar subsidios y exoneraciones. Asignado por defecto a ADMINISTRADOR + SYSTEM. _(V41, dominio nuevo `SUBSIDIES` `i-lucide-badge-percent`. **+ `SUBSIDY_VIEW_ALL`** (listar + log, admin) y **`SUBSIDY_VIEW_OWN`** (`/me`, AFILIADO+admin) para separar lectura de escritura.)_
- [x] [v2] [P0/C1] Permiso `ALLOWS_DISCOUNT` — autoriza aplicar descuentos discrecionales a un pago puntual (distinto de subsidio recurrente). Asignado por defecto a ADMINISTRADOR + SYSTEM. _(V41, dominio SUBSIDIES.)_
- [x] [v2] [P0/C1] **Decisión:** mantener `SUBSIDY_APPROVE` único para creación, revocación y modificación; no separar en `SUBSIDY_CREATE`/`SUBSIDY_REVOKE`. _(Honrada: los 3 endpoints de mutación usan `SUBSIDY_APPROVE`.)_

## Lógica de negocio

- [x] [v2] [P0/C2] `SubsidyResolver.netMonthlyFee(member, period)` consulta `subsidies` activas (member_id, valid_from ≤ period ≤ valid_until OR valid_until IS NULL) y aplica `% off` o exoneración total. Multiplica `1 - percentage/100` sobre `plan.monthly_fee`. _(V41 `SubsidyResolver` (read-only): `monthlyPercentage`/`netMonthlyFee`/`fullMonthlyExoneration`/`beneficiaryInscriptionExonerated`.)_
- [x] [v2] [P0/C2] Cuando hay más de un subsidio vigente, tomar el de mayor `percentage` — no se acumulan. _(`SubsidyResolver` toma el `max` del porcentaje entre los subsidios activos.)_
- [x] [v2] [P0/C2] La generación de `payments` respeta los subsidios; en el recibo se muestra "Subsidio aplicado: X%". _(La solvencia y el monto neto ya respetan el subsidio (`MembershipStatusService` + `SubsidyResolver`). Los pagos son manuales (ADR 0008) — no hay generación automática; la **línea impresa "Subsidio aplicado"** se cablea cuando aterrice el motor de documentos (ADR 0012, hoy congelado). Enganche listo vía `SubsidyResolver`.)_
- [x] [v2] [P0/C2] Toda operación CRUD de subsidios graba en `subsidy_audit_log` con `actor_id` del JWT. _(`SubsidiesService.create/update/revoke` escriben CREATED/MODIFIED/REVOKED con snapshot before/after + actor.)_

## Endpoints

- [x] [v2] [P0/C2] `POST /v1/admin/subsidies` — crea subsidio (requiere `SUBSIDY_APPROVE`, exige `reason` no vacío). _(`AdminSubsidyController`; valida cobertura, ventana, beneficiarios del member y tope.)_
- [x] [v2] [P0/C2] `PUT /v1/admin/subsidies/{uuid}` — modifica (requiere `SUBSIDY_APPROVE`). _(PATCH-style; una lista `beneficiaries` no nula reemplaza el set.)_
- [x] [v2] [P0/C2] `DELETE /v1/admin/subsidies/{uuid}` — revoca (soft, marca `active=false`). _(Escribe audit REVOKED.)_
- [x] [v2] [P0/C2] `GET /v1/admin/subsidies?memberUuid=...` + RSQL — consulta con paginación. _(`SUBSIDY_VIEW_ALL`; filtro `memberUuid` + RSQL `filter` + free-text `q` sobre `reason`.)_
- [x] [v2] [P0/C2] `GET /v1/admin/subsidies/{uuid}/log` — historial de auditoría del subsidio. _(`SUBSIDY_VIEW_ALL`.)_
- [x] [v2] [P0/C2] `GET /v1/me/subsidies` — el titular ve sus subsidios activos (transparencia, no edición). _(`MySubsidiesController`, `SUBSIDY_VIEW_OWN`; resuelve el member por `user→person→member`, 404 `me.member.not_enrolled`.)_

## Descuentos puntuales (permiso `ALLOWS_DISCOUNT`)

- [x] [v2] [P0/C2] Distinto de subsidios: aplica a UN `payment` específico (ej. condonar una mensualidad puntual). No es recurrente. _(Columnas `payments.discount_amount/discount_reason/discounted_by/discounted_at` en V41, con CHECK de coherencia + `discount ≤ amount`.)_
- [x] [v2] [P0/C2] `POST /v1/admin/payments/{uuid}/discount` — aplica descuento al pago en estado PENDING (requiere `ALLOWS_DISCOUNT`, exige `reason`). _(`PaymentsService.applyDiscount` — solo PENDING (422 `payment.discount.not_pending`), monto ≤ pago (422 `payment.discount.exceeds_amount`). **El descuento se audita en la propia fila** (who/when/why/monto): el `payment_audit_log` que este checklist asumía "existente en vertical-6" **no existe** — se difiere como generalización futura.)_

## Pendientes (TBD) — capturar con cliente

- [x] [v2] [P0/C1] **TBD:** ¿parciales (X% off) o solo 100%? _(Resuelto por el diseño: `monthly_percentage`/`inscription_percentage` soportan 0–100 → ambos casos.)_
- [ ] [v2] [P0/C1] **TBD:** ¿se notifica al titular cuando se le otorga/revoca un subsidio? (email/in-app). _(Follow-up: `NotificationService` ya existe (vertical-9) — falta encolar `subsidy-granted`/`subsidy-revoked`.)_

## Cambios asociados en otros verticals

> Subsidios no es un módulo aislado: cambia cómo se computa la solvencia y cómo se generan los recibos.

- **vertical-5 (Planes y Membresías)** — "Solvencia con subsidio activo": `MembershipStatusService` reconoce subsidio 100% de mensualidad activo como `ACTIVE` sin requerir fila en `payments`; el sweep diario consulta subsidios antes de degradar. Subsidio parcial (< 100%) sí exige payment del monto reducido. **Implementado en V41** (los 4 ítems de esa sección). _Ítem PDF #1.b._
- **vertical-6 (Pagos manuales)** — el recibo debe mostrar "Subsidio aplicado: X%" como línea separada. **Pendiente del motor de documentos** (ADR 0012 congelado); enganche listo en `SubsidyResolver`.
