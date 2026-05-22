# Alcance 8 — Promotores, Comisiones y Referidos

> Red de ventas: comisión automática al aprobar pago inicial + códigos de descuento.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C3] `V24__promoters.sql`
- [ ] [P0/C3] `V25__commissions.sql`
- [ ] [P0/C3] `V26__referrals.sql`

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
