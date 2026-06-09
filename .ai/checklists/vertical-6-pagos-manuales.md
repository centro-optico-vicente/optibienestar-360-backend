# Alcance 6 — Pagos manuales

> Flujo: registro → revisión humana → aprobación/rechazo + email + comisión.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V23__payments.sql` _(Al final de V23 agregar el FK constraint diferido desde V18: `ALTER TABLE beneficiaries ADD CONSTRAINT fk_beneficiaries_inscription_payment FOREIGN KEY (inscription_payment_id) REFERENCES payments(payments_id);` — la columna `inscription_payment_id BIGINT` ya existe en beneficiaries sin FK por orden de carga.)_
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
