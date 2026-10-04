SET search_path TO app, public;

-- ============================================================================
-- V176: PROMOTION_NOTICES daily job (hub ADR 0018). Emails members with an
-- ongoing promotion `daysBeforeDue` days ahead of their next payment: pay on
-- time to keep an ACQUISITION discount, or the upcoming charge is the last
-- discounted one.
-- ============================================================================

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters)
VALUES (
    'PROMOTION_NOTICES',
    'Avisos de promociones',
    'Avisa a los afiliados con una promoción activa, daysBeforeDue días antes de su próximo pago: que paguen a tiempo para no perder el descuento, o que su promoción termina con ese pago.',
    '0 0 8 * * *',
    'America/Caracas',
    '{"daysBeforeDue": 5}'::jsonb
)
ON CONFLICT (code) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'PROMOTION_NOTICES') THEN
        RAISE EXCEPTION 'V176: PROMOTION_NOTICES was not seeded';
    END IF;
END $$;
