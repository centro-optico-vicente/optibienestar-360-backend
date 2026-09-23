SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V151: phase 3 automation of the commission-settlement frequency engine
-- (hub plan "commission-frequency-currency-unification") — seeds the three
-- scheduled_jobs rows that drive CommissionTierSettlementCutJobRunner,
-- HierarchyOverrideSettlementCutJobRunner and
-- CommissionRetroactiveSettlementCutJobRunner. Everything before this
-- migration was manual-only via AdminCommissionController.
--
-- Staggered daily, after the day's operations close and clear of every
-- other scheduled job's slot: MEMBERSHIP_STATUS_SWEEP already owns 03:00
-- daily (V22), MEMBERSHIP_DUE_SOON 06:00 / MEMBERSHIP_GRACE 06:30 daily
-- (V40), BONUS_EVALUATION 04:00 / LEADERBOARD_PRIZE_AWARD 05:00 (both
-- 1st-of-month only, V37/V42), FETCH_EXCHANGE_RATES 16:00 Mon-Fri (V143).
-- The three new jobs run BEFORE the membership status sweep, in the
-- 02:00-02:40 window, so nothing above them collides either:
--   02:00 COMMISSION_TIER_SETTLEMENT_CUT
--   02:20 HIERARCHY_OVERRIDE_SETTLEMENT_CUT
--   02:40 COMMISSION_RETROACTIVE_SETTLEMENT_CUT
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'COMMISSION_TIER_SETTLEMENT_CUT',
    'Corte automático de comisiones directas',
    'Recorre las reglas activas de commission_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) las comisiones APPROVED de tipo INSCRIPTION de cada promotor con actividad en el corte. Reutiliza CommissionPeriodicSettlementService.settleCut por promotor.',
    '0 0 2 * * *',
    'America/Caracas'
);

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'HIERARCHY_OVERRIDE_SETTLEMENT_CUT',
    'Corte automático de comisiones jerárquicas',
    'Recorre las reglas activas de hierarchy_override_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) los overrides PENDING con comisión raíz APPROVED de cada beneficiario con actividad en el corte. Reutiliza HierarchyOverridePeriodicSettlementService.settleCut por beneficiario.',
    '0 20 2 * * *',
    'America/Caracas'
);

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'COMMISSION_RETROACTIVE_SETTLEMENT_CUT',
    'Corte automático de retroactivos de comisión',
    'Invoca diariamente CommissionRetroactiveTopUpService.executeCut(asOf=hoy), que ya resuelve internamente — por cada beneficiario/regla de los 4 ledger types — si hoy cierra su corte de liquidación retroactiva, calculando y persistiendo el retroactivo solo cuando corresponde. Idempotente y no destructivo (upsert solo sobre filas PENDING).',
    '0 40 2 * * *',
    'America/Caracas'
);

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM scheduled_jobs WHERE code IN (
        'COMMISSION_TIER_SETTLEMENT_CUT', 'HIERARCHY_OVERRIDE_SETTLEMENT_CUT', 'COMMISSION_RETROACTIVE_SETTLEMENT_CUT'
    )) <> 3 THEN
        RAISE EXCEPTION 'V151: one or more commission-settlement-automation scheduled_jobs rows were not created';
    END IF;
END $$;
