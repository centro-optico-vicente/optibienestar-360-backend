# Alcance 9 — Notificaciones y Carnet digital

> Cola persistente de emails + jobs diarios de vencimientos + QR del afiliado.
> Índice: [../../checklist-vertical.md](../../checklist-vertical.md)

## Migraciones

- [ ] [P0/C2] `V26__notifications.sql`
- [ ] [P0/C2] `V27__digital_cards_view.sql`

## Código

- [ ] [P1/C2] Cola async `@Async` + reintentos exponenciales (Tarea 1.8 pendiente)
- [ ] [P0/C2] Entidad Notification (cola persistente)
- [ ] [P0/C3] `NotificationService`: enqueue + envío async + reintentos
- [ ] [P0/C2] Templates: `welcome.html`, `payment-reminder.html`, `payment-overdue.html`, `membership-suspended.html`, `membership-expired.html`, `referral-reward.html`, `commission-payout.html`
- [ ] [P0/C3] Job diario — detecta vencimientos próximos (3 días antes de `next_due_date`)
- [ ] [P0/C3] Job diario — detecta vencidos en período de gracia
- [ ] [P0/C2] `GET /v1/me/digital-card` + QR generado
- [ ] [P0/C2] `GET /v1/me/family` + `/v1/me/usage-history`
