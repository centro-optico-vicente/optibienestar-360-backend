SET search_path TO app, public;

-- ============================================================================
-- V146: unifies the settlement-frequency naming across the 4 commission-rule
-- entities (hub plan commission-frequency-currency-unification, Fase A). This
-- table's only existing frequency axis is `window_strategy` — renamed to
-- `accrual_period_strategy` to match the same axis name on
-- commission_tiers/hierarchy_override_tiers (`period_strategy`, renamed in
-- this same phase — see V147/V148) and collection_commission_tiers (new in
-- V149). Two more axes join it, mirroring V112's payout/settlement split:
--
--   accrual_period_strategy     — the window the metric is measured against
--                                  (renamed from window_strategy).
--   partial_settlement_period_strategy — how often a partial disbursement of
--                                  an already-accrued award is paid out.
--   final_settlement_period_strategy   — the containing window whose close
--                                  triggers the award's final settlement.
--   retroactive_settlement_period_strategy — the window whose close triggers
--                                  a retroactive catch-up settlement.
--
-- Default MONTHLY/MONTHLY on the two new settlement columns is deliberately a
-- no-op, same reasoning as V112: PeriodCutCalculator.cuts('MONTHLY', ...)
-- returns exactly one cut equal to the whole month, so a rule that never
-- configures these settles exactly like it does today. `accrual_period_strategy`
-- is backfilled from the renamed column itself (no-op by construction).
--
-- CAMPAIGN/LIFETIME (only meaningful for accrual — a rule can be anchored to
-- a campaign or run lifetime-cumulative, but "settle every campaign" or
-- "settle once ever" are not periodic settlement cadences) are excluded from
-- the 3 new settlement-axis CHECKs; the pre-existing accrual CHECK keeps
-- both. Anchors (day-of-week 1-7 for WEEKLY/BIWEEKLY, day-of-month 1-31 for
-- MONTHLY+) are added nullable for all 4 axes — interpretation is phase 2
-- (PeriodStrategies), not this migration.
-- ============================================================================

ALTER TABLE commission_bonus_rules
    RENAME COLUMN window_strategy TO accrual_period_strategy;

ALTER TABLE commission_bonus_rules
    RENAME CONSTRAINT chk_bonus_rule_window
        TO chk_bonus_rule_accrual_period_strategy;

ALTER TABLE commission_bonus_rules
    ADD COLUMN partial_settlement_period_strategy     VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN final_settlement_period_strategy       VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN retroactive_settlement_period_strategy VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE commission_bonus_rules
    ADD CONSTRAINT chk_commission_bonus_rules_partial_settlement_period_strategy
        CHECK (partial_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_commission_bonus_rules_final_settlement_period_strategy
        CHECK (final_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_commission_bonus_rules_retroactive_settlement_period_strategy
        CHECK (retroactive_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));

ALTER TABLE commission_bonus_rules
    ADD COLUMN accrual_period_anchor               SMALLINT,
    ADD COLUMN partial_settlement_period_anchor     SMALLINT,
    ADD COLUMN final_settlement_period_anchor       SMALLINT,
    ADD COLUMN retroactive_settlement_period_anchor SMALLINT;

ALTER TABLE commission_bonus_rules
    ADD CONSTRAINT chk_commission_bonus_rules_accrual_period_anchor_range
        CHECK (accrual_period_anchor IS NULL OR accrual_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_commission_bonus_rules_partial_settlement_period_anchor_range
        CHECK (partial_settlement_period_anchor IS NULL OR partial_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_commission_bonus_rules_final_settlement_period_anchor_range
        CHECK (final_settlement_period_anchor IS NULL OR final_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_commission_bonus_rules_retroactive_settlement_period_anchor_range
        CHECK (retroactive_settlement_period_anchor IS NULL OR retroactive_settlement_period_anchor BETWEEN 1 AND 31);


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM commission_bonus_rules
        WHERE accrual_period_strategy IS NULL
           OR partial_settlement_period_strategy IS NULL
           OR final_settlement_period_strategy IS NULL
           OR retroactive_settlement_period_strategy IS NULL
    ) THEN
        RAISE EXCEPTION 'V146: commission_bonus_rules has NULL period strategy after backfill';
    END IF;
END $$;
