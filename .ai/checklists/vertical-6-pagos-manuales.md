# Alcance 6 — Pagos manuales

> Flujo: registro → revisión humana → aprobación/rechazo + email + comisión.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [x] [P0/C3] `V23__payments.sql` _(Schema: `membership_id` FK + `payer_user_id` FK NULL (cash at counter sin user) + money (`amount NUMERIC(10,2) > 0` + `currency` default USD) + `payment_method` CHECK ENUM 7 valores (BANK_TRANSFER/CASH/ZELLE/PAGO_MOVIL/CRYPTO/INTERNATIONAL_TRANSFER/OTHER) + `reference_number` + `payment_date` (settlement bank) + `received_at` (system) + **allocation** (`inscription BOOLEAN` + `applied_period DATE` primer día del mes para recurring) + **proof of payment** (R2 key + nombre + content_type + size_bytes) + `admin_notes` + **review workflow** (`reviewed_by BIGINT FK users + reviewed_at + review_reason TEXT`) + status CHECK (PENDING/APPROVED/REJECTED) + audit BaseEntity. **CHECK constraints**: (1) `review_consistency` — PENDING ⇔ reviewed_at/by NULL; APPROVED|REJECTED ⇔ reviewed_at/by NOT NULL; (2) `rejection_has_reason` — REJECTED ⇒ review_reason NOT NULL; (3) `date_not_future` — payment_date ≤ today+1d (anti typo); (4) `inscription_no_period` — inscription ⇒ applied_period NULL (es one-time, no aligned a mes). **Índices**: `(status, received_at DESC) WHERE is_active` para cola pending del admin; `(membership_id, payment_date DESC)` para historial; `(applied_period) WHERE is_active AND status='APPROVED'` para reportes mensuales. **Reviewed_by usa BIGINT** (joins cheap, matchea V11 ally_services pattern). **Currency** queda en USD por default — VES y conversión FX rates van en v2. **FK diferido aplicado al final**: `ALTER TABLE beneficiaries ADD CONSTRAINT fk_beneficiaries_inscription_payment FOREIGN KEY (inscription_payment_id) REFERENCES payments(payments_id)` — ON DELETE NO ACTION (soft-delete es el patrón canónico). contextLoads V1..V23 limpio.)_
- [ ] [P0/C2] Índice: `payments(status)`

## Código

- [ ] [P0/C2] Entidad Payment
- [ ] [P0/C3] `POST /v1/admin/payments` (multipart soporte → R2)
- [ ] [P0/C3] `PUT /v1/admin/payments/{id}/approve`
- [ ] [P0/C2] `PUT /v1/admin/payments/{id}/reject`
- [ ] [P0/C2] `GET /v1/admin/payments` + RSQL
- [ ] [P0/C2] `GET /v1/admin/payments/{id}/support` (presigned URL)
- [ ] [P0/C2] `GET /v1/me/payments`
- [ ] [P0/C2] Templates `payment-received.html` + `payment-approved.html` + `payment-rejected.html`
