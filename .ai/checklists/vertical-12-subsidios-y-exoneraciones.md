# Alcance 12 — Subsidios y Exoneraciones (Adicionales v2)

> 🆕 **Vertical nuevo, parte de Adicionales v2.** Ítem PDF #1 — "Gestión de Subsidios y Exoneraciones".
> Ver mapa completo en [`../scope-additions-v2.md`](../scope-additions-v2.md).
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)
>
> **Caso de uso (PDF):** controles para omitir o subsidiar el pago de mensualidades a perfiles especiales (fundaciones, iglesias, casos de bajos recursos) manteniendo el registro formal en auditoría.

## Migraciones

- [ ] [v2] [P0/C2] Tabla `subsidies`: `uuid`, `member_id` FK, `percentage` NUMERIC(5,2) (0-100, donde 100=exoneración total y X% = subsidio parcial), `reason` TEXT, `authorized_by` FK users, `valid_from` DATE, `valid_until` DATE NULL (NULL = indefinido), `active` bool, audit fields.
- [ ] [v2] [P0/C2] Índice `subsidies(member_id, active, valid_from, valid_until)` para resolver "¿tiene subsidio activo hoy?".
- [ ] [v2] [P0/C2] Tabla `subsidy_audit_log`: historial inmutable (`subsidy_id`, `action` ENUM('CREATED','REVOKED','MODIFIED'), `actor_id`, `at`, `before`, `after`, `reason`). El PDF exige "registro formal en auditoría".

## Permisos (V6 ampliación o nueva migración Vx)

- [ ] [v2] [P0/C1] Permiso `SUBSIDY_APPROVE` — autoriza crear/revocar subsidios y exoneraciones. Asignado por defecto a ADMINISTRADOR + SYSTEM.
- [ ] [v2] [P0/C1] Permiso `ALLOWS_DISCOUNT` — autoriza aplicar descuentos discrecionales a un pago puntual (distinto de subsidio recurrente). Asignado por defecto a ADMINISTRADOR + SYSTEM.
- [ ] [v2] [P0/C1] **Decisión:** mantener `SUBSIDY_APPROVE` único para creación, revocación y modificación; no separar en `SUBSIDY_CREATE`/`SUBSIDY_REVOKE` por simplicidad operativa (ambos los hace el mismo rol administrativo).

## Lógica de negocio

- [ ] [v2] [P0/C2] `MembershipPaymentService.calculateAmount(member, period)` consulta `subsidies` activas (member_id, valid_from ≤ period ≤ valid_until OR valid_until IS NULL) y aplica `% off` o exoneración total. Multiplica `1 - percentage/100` sobre `plan.monthly_fee`.
- [ ] [v2] [P0/C2] Cuando hay más de un subsidio vigente (caso raro pero posible), tomar el de mayor `percentage` — no se acumulan.
- [ ] [v2] [P0/C2] Generación mensual de `payments` respeta los subsidios automáticamente; en el recibo se muestra "Subsidio aplicado: X%" como línea separada.
- [ ] [v2] [P0/C2] Toda operación CRUD de subsidios graba en `subsidy_audit_log` con `actor_id` del JWT.

## Endpoints

- [ ] [v2] [P0/C2] `POST /v1/admin/subsidies` — crea subsidio (requiere `SUBSIDY_APPROVE`, exige `reason` no vacío).
- [ ] [v2] [P0/C2] `PUT /v1/admin/subsidies/{uuid}` — modifica (requiere `SUBSIDY_APPROVE`).
- [ ] [v2] [P0/C2] `DELETE /v1/admin/subsidies/{uuid}` — revoca (soft, marca `active=false`).
- [ ] [v2] [P0/C2] `GET /v1/admin/subsidies?memberId=...` + RSQL — consulta con paginación.
- [ ] [v2] [P0/C2] `GET /v1/admin/subsidies/{uuid}/log` — historial de auditoría del subsidio.
- [ ] [v2] [P0/C2] `GET /v1/me/subsidies` — el titular ve sus subsidios activos (transparencia, no edición).

## Descuentos puntuales (permiso `ALLOWS_DISCOUNT`)

- [ ] [v2] [P0/C2] Distinto de subsidios: aplica a UN `payment` específico (ej. condonar una mensualidad puntual por error). No es recurrente.
- [ ] [v2] [P0/C2] `POST /v1/admin/payments/{uuid}/discount` — aplica descuento al pago en estado PENDING (requiere `ALLOWS_DISCOUNT`, exige `reason`). Graba en `payment_audit_log` (existente en vertical-6).

## Pendientes (TBD) — capturar con cliente

- [ ] [v2] [P0/C1] **TBD:** ¿se permiten subsidios parciales (X% off) o solo exoneración 100%? La columna `percentage` los soporta; pregunta para el contrato comercial.
- [ ] [v2] [P0/C1] **TBD:** ¿se notifica al titular cuando se le otorga/revoca un subsidio? (email/in-app) — depende del módulo notificaciones.
