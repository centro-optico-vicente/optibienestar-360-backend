SET search_path TO app, public;

-- ============================================================================
-- V112: per-rule partial-cut settlement config (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §3 "Liquidación
-- parcial por frecuencia configurable + retroactivo al cierre").
--
-- commission_tiers and hierarchy_override_tiers already carry a
-- `period_strategy` column (V42 / V102) — that one sizes the *volume window*
-- a tier's threshold_count is measured against (e.g. "reached 1500 in the
-- MONTHLY window"). It says nothing about *when a promoter actually gets
-- paid* within that window. This migration adds that second, independent
-- axis per rule:
--
--   payout_period_strategy      — how often a partial cut is disbursed
--                                  (e.g. WEEKLY payouts).
--   settlement_period_strategy  — the containing window whose close triggers
--                                  the retroactive top-up to the final
--                                  highest-qualifying band (e.g. MONTHLY).
--
-- Default MONTHLY/MONTHLY on every existing + new row is deliberately a
-- no-op: PeriodCutCalculator.cuts('MONTHLY', <month start>, <month end>)
-- returns exactly one cut equal to the whole month, so a rule that never
-- configures these two columns settles exactly like it does today — a
-- single month-end payout, no partial cuts, nothing retroactive to catch up.
-- ============================================================================

ALTER TABLE commission_tiers
    ADD COLUMN payout_period_strategy     VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN settlement_period_strategy VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE commission_tiers
    ADD CONSTRAINT chk_commission_tiers_payout_period_strategy
        CHECK (payout_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_commission_tiers_settlement_period_strategy
        CHECK (settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));

ALTER TABLE hierarchy_override_tiers
    ADD COLUMN payout_period_strategy     VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN settlement_period_strategy VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE hierarchy_override_tiers
    ADD CONSTRAINT chk_hierarchy_override_tiers_payout_period_strategy
        CHECK (payout_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_hierarchy_override_tiers_settlement_period_strategy
        CHECK (settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM commission_tiers
        WHERE payout_period_strategy IS NULL OR settlement_period_strategy IS NULL
    ) THEN
        RAISE EXCEPTION 'V112: commission_tiers has NULL payout/settlement period strategy after backfill';
    END IF;
    IF EXISTS (
        SELECT 1 FROM hierarchy_override_tiers
        WHERE payout_period_strategy IS NULL OR settlement_period_strategy IS NULL
    ) THEN
        RAISE EXCEPTION 'V112: hierarchy_override_tiers has NULL payout/settlement period strategy after backfill';
    END IF;
END $$;
