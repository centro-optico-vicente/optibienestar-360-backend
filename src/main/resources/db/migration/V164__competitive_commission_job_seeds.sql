SET search_path TO app, public;

-- ============================================================================
-- V164: Fase 2b (hub plan competitive-commission-rules) — scheduled_jobs rows
-- for CompetitiveCommissionEvaluationJobRunner (every 15 minutes: keeps
-- PROVISIONAL awards live and confirms whatever's past its
-- confirmation_delay_days) and CompetitiveCommissionSettlementCutJobRunner
-- (daily 03:20 — after MEMBERSHIP_STATUS_SWEEP at 03:00, before BONUS_
-- EVALUATION at 04:00 on day 1; free slot, no ordering dependency).
-- ============================================================================

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'COMPETITIVE_COMMISSION_EVALUATION',
    'Evaluación de reglas de comisión competitivas',
    'Recorre las reglas competitivas activas, recalcula posiciones y premios provisionales para el periodo abierto y confirma (PROVISIONAL → PENDING) los que ya vencieron su confirmation_delay_days.',
    '0 */15 * * * *',
    'America/Caracas'
),
(
    'COMPETITIVE_COMMISSION_SETTLEMENT_CUT',
    'Corte de liquidación de comisiones competitivas',
    'Recorre las reglas competitivas activas y, para cada premio confirmado (PENDING/PAID) cuyo corte de liquidación (parcial, retroactivo o final) cierra hoy, calcula y registra el monto adeudado como un nuevo settlement PENDING.',
    '0 20 3 * * *',
    'America/Caracas'
);

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM scheduled_jobs WHERE code IN
        ('COMPETITIVE_COMMISSION_EVALUATION', 'COMPETITIVE_COMMISSION_SETTLEMENT_CUT')) <> 2 THEN
        RAISE EXCEPTION 'V164: competitive commission scheduled_jobs rows were not created';
    END IF;
END $$;
