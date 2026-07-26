SET search_path TO app, public;

-- V40: scheduled-job rows for the notification queue worker and the two
-- membership-reminder jobs (vertical-9). Resolved to their ScheduledJobRunner
-- beans by code. All run non-concurrent (allow_concurrent defaults FALSE in
-- V22) in America/Caracas (ADR 0010).
--
--   NOTIFICATION_DISPATCH  — drains the persistent queue (V28); every 5 min.
--   MEMBERSHIP_DUE_SOON    — payment reminder 3 days before next_due_date; daily.
--   MEMBERSHIP_GRACE       — payment-overdue nudge during grace; daily, after
--                            the 03:00 status sweep so suspensions are settled.


INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES
    ('NOTIFICATION_DISPATCH',
     'Despacho de la cola de notificaciones',
     'Recorre la cola persistente de notificaciones (V28): envía los PENDING vencidos y reintenta los FAILED cuyo backoff expiró. Marca SENT / FAILED (con backoff) / DEAD_LETTER por fila.',
     '0 */5 * * * *',
     'America/Caracas'),

    ('MEMBERSHIP_DUE_SOON',
     'Recordatorio de vencimiento próximo',
     'Encola un recordatorio de pago (payment-reminder) para las membresías ACTIVE cuya próxima cuota vence en exactamente 3 días.',
     '0 0 6 * * *',
     'America/Caracas'),

    ('MEMBERSHIP_GRACE',
     'Aviso de pago vencido en gracia',
     'Encola un aviso (payment-overdue) para las membresías SUSPENDED dentro del período de gracia, unos días antes de que expiren.',
     '0 30 6 * * *',
     'America/Caracas');


-- Fail loudly rather than migrate into a half-applied state.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('NOTIFICATION_DISPATCH'), ('MEMBERSHIP_DUE_SOON'), ('MEMBERSHIP_GRACE')
        ) AS want(code)
        WHERE NOT EXISTS (SELECT 1 FROM scheduled_jobs sj WHERE sj.code = want.code)
    ) THEN
        RAISE EXCEPTION 'V40: one or more notification/reminder scheduled_jobs rows were not seeded';
    END IF;
END $$;
