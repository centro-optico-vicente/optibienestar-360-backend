SET search_path TO app, public;

-- ============================================================================
-- V149: adds the 4-axis settlement-frequency config to collection_commission_
-- tiers (hub plan commission-frequency-currency-unification, Fase A). Unlike
-- commission_tiers/hierarchy_override_tiers, this table never had ANY
-- frequency column — CollectionCommissionTiersService hardcodes MONTHLY
-- evaluation today. This migration only adds the columns (phase 2 wires the
-- service to actually read them); every value defaults to 'MONTHLY', which
-- is a deliberate no-op that reproduces today's hardcoded-monthly behavior
-- exactly, both for existing rows and for any row inserted before phase 2
-- lands.
--
--   accrual_period_strategy                — window the collection metric is measured against.
--   partial_settlement_period_strategy     — how often a partial cut is disbursed.
--   final_settlement_period_strategy       — window whose close triggers final settlement.
--   retroactive_settlement_period_strategy — window whose close triggers a retroactive catch-up.
--
-- Anchors (day-of-week 1-7 for WEEKLY/BIWEEKLY, day-of-month 1-31 for
-- MONTHLY+) are added nullable for all 4 axes — interpretation is phase 2
-- (PeriodStrategies), not this migration.
-- ============================================================================

ALTER TABLE collection_commission_tiers
    ADD COLUMN accrual_period_strategy               VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN partial_settlement_period_strategy     VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN final_settlement_period_strategy       VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN retroactive_settlement_period_strategy VARCHAR(20) NOT NULL DEFAULT 'MONTHLY';

ALTER TABLE collection_commission_tiers
    ADD CONSTRAINT chk_collection_commission_tiers_accrual_period_strategy
        CHECK (accrual_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_collection_commission_tiers_partial_settlement_period_strategy
        CHECK (partial_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_collection_commission_tiers_final_settlement_period_strategy
        CHECK (final_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL')),
    ADD CONSTRAINT chk_collection_commission_tiers_retroactive_settlement_period_strategy
        CHECK (retroactive_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));

ALTER TABLE collection_commission_tiers
    ADD COLUMN accrual_period_anchor               SMALLINT,
    ADD COLUMN partial_settlement_period_anchor     SMALLINT,
    ADD COLUMN final_settlement_period_anchor       SMALLINT,
    ADD COLUMN retroactive_settlement_period_anchor SMALLINT;

ALTER TABLE collection_commission_tiers
    ADD CONSTRAINT chk_collection_commission_tiers_accrual_period_anchor_range
        CHECK (accrual_period_anchor IS NULL OR accrual_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_collection_commission_tiers_partial_settlement_period_anchor_range
        CHECK (partial_settlement_period_anchor IS NULL OR partial_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_collection_commission_tiers_final_settlement_period_anchor_range
        CHECK (final_settlement_period_anchor IS NULL OR final_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_collection_commission_tiers_retroactive_settlement_period_anchor_range
        CHECK (retroactive_settlement_period_anchor IS NULL OR retroactive_settlement_period_anchor BETWEEN 1 AND 31);


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM collection_commission_tiers
        WHERE accrual_period_strategy IS NULL
           OR partial_settlement_period_strategy IS NULL
           OR final_settlement_period_strategy IS NULL
           OR retroactive_settlement_period_strategy IS NULL
    ) THEN
        RAISE EXCEPTION 'V149: collection_commission_tiers has NULL period strategy after backfill';
    END IF;
END $$;
