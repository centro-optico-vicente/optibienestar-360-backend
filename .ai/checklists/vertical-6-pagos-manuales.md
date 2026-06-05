# Alcance 6 — Pagos manuales

> Flujo: registro → revisión humana → aprobación/rechazo + email + comisión.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V21__payments.sql`
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
