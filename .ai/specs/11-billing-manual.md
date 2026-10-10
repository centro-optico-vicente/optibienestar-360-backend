# 11 — Workflow de pagos manuales

> Implementa [ADR 0008 cross-stack](../../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md) y su enmienda [ADR 0023 — modelo unificado](../../../centro-optico-vicente/.ai/decisions/0023-unified-payments-model-reality.md).
>
> **Reescrito 2026-10-09** (ver [auditoría 2026-09-23](../../../centro-optico-vicente/.ai/notes/2026-09-23_audit.md)): la versión anterior describía un `Payment` plano con estado `PENDING_REVIEW`/`APPROVED`/`REJECTED` — eso nunca existió así. El modelo real es header (`Payment`, V23) + `PaymentLine` (V117) desde que se unificaron cobros y pagos a promotores. Todo lo de abajo está verificado contra `PaymentsService.java` (1453 líneas), `AdminPaymentController.java`, las entidades y los DTOs reales — no inventado.

## Modelo real

**`Payment`** (header, tabla `payments`) — `direction` `IN` (cobro a un afiliado) u `OUT` (pago de comisión a un promotor, lo escribe `CommissionPayoutService`, no este flujo). Un `Payment` siempre tiene `paymentType` (FK `PaymentCategory` = el **motivo**: `MEMBERSHIP_FEE`/`INSCRIPTION_FEE`/etc.), `person`, `amount`, `currency`, `paymentDate`, `receivedAt`, y para `IN` además `membership`, `inscription` (boolean), `appliedPeriod`/`coverageThroughPeriod` (mes(es) cubiertos), soporte de archivo (`supportFileUrl`/`Name`/`ContentType`/`SizeBytes`), descuento opcional (`discountAmount`/`Reason`/`discountedBy`/`discountedAt`) y `netAmount()` = `amount - discountAmount`.

**`PaymentLine`** (tabla `payment_lines`, 1:N desde `Payment`) — una línea por forma de pago real: `paymentType` (FK `PaymentMethod` = el **método**, no confundir con el de arriba), `bank` (si el método lo exige), `amount`, `currency`, datos del instrumento (`identification`, `bankAccountType/Code/Identifier`, `phone`, `email`, `referenceNumber`), y su propio `status`/`reviewedBy`/`reviewedAt`/`reviewReason`. Hoy en la práctica cada `Payment` sigue teniendo una sola línea, pero el modelo soporta varias (registrar un cobro partido entre efectivo + transferencia, por ejemplo).

**Estados reales** (`Payment.PaymentStatus`, enum real en el código — ojo que su propio Javadoc dice "exactly these three" pero son 4):

```
DRAFT ──(submit)──► PENDING ──(approve)──► APPROVED
  ▲                    │
  └──(reactivate)──────┘
                        └──(reject)───────► REJECTED
```

- `DRAFT`: solo si se registró con `?draft=true` — las líneas quedan editables (`PUT .../lines`) hasta un `submit` explícito. El flujo de un solo paso (`draft` omitido/`false`) sigue existiendo sin cambios: pasa directo a `PENDING` (o a `APPROVED`, ver auto-aprobación abajo).
- `PENDING → DRAFT` (`reactivate`) es la única forma de volver a editar las líneas de un pago ya sometido.
- `APPROVED`/`REJECTED` son terminales — no hay vuelta atrás salvo registrar un pago nuevo.

**Auto-aprobación (V158):** si un pago no-draft tiene **todas** sus líneas con un método de "recepción directa" (`PaymentMethod.requiresApproval = false`, ej. efectivo), salta `PENDING` entero y nace directo en `APPROVED` — no hay nada que verificar contra un estado de cuenta de un tercero.

## Endpoints reales (`AdminPaymentController`, base `/v1/admin/payments`)

| Método | Path | Permiso | Qué hace |
|---|---|---|---|
| GET | `` | `COLLECTION_VIEW_ALL` o `PAYMENT_VIEW_ALL` | Lista — `?direction=IN\|OUT\|ALL` (default `IN`), `?filter=` RSQL, `?q=` texto libre |
| GET | `/{uuid}` | `COLLECTION_VIEW_ALL` o `PAYMENT_VIEW_ALL` | Detalle |
| POST (multipart) | `` | `COLLECTION_CREATE` | Registra un cobro (`IN`). Partes: `payment` (JSON, `PaymentCreateRequest`) + `support` (archivo, opcional). `?draft=true` para empezar en `DRAFT` |
| PUT | `/{uuid}/lines` | `COLLECTION_CREATE` | Reemplaza las líneas de un `DRAFT` (`PaymentLinesUpdateRequest`) |
| PUT | `/{uuid}/submit` | `COLLECTION_CREATE` | `DRAFT → PENDING` |
| PUT | `/{uuid}/reactivate` | `COLLECTION_DELETE` | `PENDING → DRAFT` |
| PUT | `/{uuid}/approve` | `COLLECTION_APPROVE` | `PENDING → APPROVED` |
| PUT | `/{uuid}/reject` | `COLLECTION_REJECT` | `PENDING → REJECTED` (requiere `reason`) |
| DELETE | `/{uuid}` | `COLLECTION_DELETE` | Borra un `PENDING` (mistake); 422 si ya está `APPROVED`/`REJECTED` |
| POST (multipart) | `/out` | `PAYMENT_CREATE` | Crea un pago `OUT` (comisión) en `DRAFT` |
| PUT | `/{uuid}/out` | `PAYMENT_UPDATE` | Edita un `OUT` en `DRAFT` |
| PUT | `/{uuid}/out/process` | `PAYMENT_PROCESS` | `OUT`: `DRAFT → PENDING` |
| PUT | `/{uuid}/out/approve` | `PAYMENT_APPROVE` | `OUT`: `PENDING → APPROVED` |
| PUT | `/{uuid}/out/reject` | `PAYMENT_REJECT` | `OUT`: `PENDING → REJECTED` |
| DELETE | `/{uuid}/out` | `PAYMENT_DELETE` | Borra un `OUT` en `DRAFT` |
| GET | `/discount-authority` | `ALLOWS_DISCOUNT` | Tope de descuento (%) del actor — `null` = sin tope |
| POST | `/{uuid}/discount` | `ALLOWS_DISCOUNT` | Descuento puntual a un `PENDING` (requiere `reason`, no puede superar `amount`) |
| GET | `/{uuid}/support` | `COLLECTION_VIEW_ALL` o `PAYMENT_VIEW_ALL` | Presigned URL del soporte (`?ttlMinutes=`, clamp 1-60, default 5) |

**Importante:** los permisos de cobros (`IN`) usan el prefijo `COLLECTION_*`; los de pagos a promotores (`OUT`) usan `PAYMENT_*` — son dos familias de permisos distintas sobre la misma tabla, por dirección.

## Superficies de autoservicio (no existían en el diseño original)

- `POST /v1/me/payments` — un afiliado registra su propio pago (`registerOwn`); la membresía se resuelve del caller, nunca de un parámetro del cliente.
- `POST /v1/promoter/me/payments` — un promotor registra un cobro **de un afiliado de su propia red** (`registerForDownline`); si el afiliado no está en su cartera, 404 (no se distingue de "no existe", para no filtrar qué afiliados están fuera de su red).
- Ambas superficies tienen sus propios `updateLines`/`submit`/`reactivate`/`remove` con el mismo chequeo de pertenencia (`ownedByUser`/`ownedByPromoterDownline`).
- `GET /v1/me/payments`, `GET /v1/promoter/me/payments?direction=IN|OUT` (`IN` = cobros de la red, `OUT` = comisiones que le pagaron a él).

## Reglas reales (verificadas, no inventadas)

1. **NO hay idempotency key.** Ningún `X-Idempotency-Key` ni mecanismo equivalente existe en frontend ni backend (confirmado en esta misma sesión de auditoría) — si la red falla a mitad del registro, no hay protección automática contra duplicados.
2. **NO hay optimistic locking (`@Version`).** El entity `Payment` no tiene columna de versión — la claim del diseño original era ficticia. Dos aprobaciones concurrentes del mismo pago no están explícitamente protegidas por este mecanismo (la segunda llamada sí fallaría igual, porque `ensurePending()` exige `status == PENDING` y la primera ya lo cambió — pero es una verificación de negocio, no un lock optimista de JPA).
3. **Soporte de archivo es opcional**, no obligatorio salvo por método — la metadata siempre se captura; si R2 está apagado (`storage.r2.enabled=false`), los bytes se descartan pero la metadata queda.
4. **Campos mandatorios dependen del `PaymentMethod` elegido**, no de una regla fija por método: cada fila de `payment_methods` tiene sus propios flags `isMandatoryBank`/`isMandatoryBankAccount`/`isMandatoryPhone`/`isMandatoryEmail`/`isMandatoryReferenceNumber`.
5. **Multi-línea con regla de suma mixta**: si se mandan `lines`, la suma de sus montos debe ser `<= amount` declarado (o se auto-totaliza si no se declaró `amount`).
6. **Comisión en el pago aprobado**: al aprobar (`applyApprovalEffects`), si la dirección es `IN`, se dispara `CommissionService.calculateAndPersistFor(payment)` y, si corresponde, `HierarchyOverrideService.cascadeFrom(...)` — ambos *best-effort* (una excepción se loguea y se traga; la aprobación del pago **nunca** se revierte por un fallo de comisión). Ver [12-commissions.md](12-commissions.md).
7. **Primer pago aprobado confirma al miembro** (`confirmMemberOnFirstApprovedPayment`) — solo si el miembro no estaba ya confirmado.
8. **Snapshot de tasa de cambio** (ADR 0015) si la moneda del pago difiere de la moneda de la membresía — informativo, nunca bloquea la aprobación si no hay tasa disponible.
9. **Facturación corporativa** (V38): si el miembro pertenece a un contrato `INSTITUTION_BULK`, `CorporateBillingResolver` factura al contrato en vez de al individuo.
10. **Notificaciones best-effort**: email al afiliado (recibido/aprobado/rechazado) y notificación a promotor+admin en cada submission/decisión — cualquier fallo de SMTP se loguea y se ignora, nunca revierte la transacción.

## Lo que NO existe (corrige afirmaciones previas de este documento)

- No hay `PaymentMethod` como *enum* Java — es un catálogo de BD (`payment_methods`, V115), administrable sin deploy.
- No hay endpoints `/v1/admin/payments/summary` ni `/v1/admin/payments/export` — no confirmados en el código actual; si existen, están en otro controller no revisado en esta pasada.
- No hay `/v1/admin/payments?filter=method==ZELLE` — `method` ya no es una columna de `Payment` filtrable por RSQL (vive en `payment_lines`); los campos RSQL permitidos hoy son `status`, `currency.code`, `amount`, `inscription`, `paymentDate`, `receivedAt`, `appliedPeriod`, `reviewedAt`, `createdAt`, `updatedAt`, `active` (`PaymentsService.ALLOWED_FILTER_FIELDS`).

## Referencias

- [ADR 0008 — Manual payments](../../../centro-optico-vicente/.ai/decisions/0008-manual-payments.md)
- [ADR 0023 — Modelo unificado real](../../../centro-optico-vicente/.ai/decisions/0023-unified-payments-model-reality.md)
- [12-commissions.md](12-commissions.md) — cuándo se crea la comisión, cómo se paga
- [10-validators.md](10-validators.md) — invalidación cache del validador
