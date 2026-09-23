SET search_path TO app, public;

-- ============================================================================
-- V147: commission_tiers half of the settlement-frequency naming unification
-- (hub plan commission-frequency-currency-unification, Fase A) plus a new
-- amount-basis threshold. Renames the 3 existing period axes to the shared
-- names used across all 4 commission-rule entities:
--
--   period_strategy          -> accrual_period_strategy   (V42)
--   payout_period_strategy   -> partial_settlement_period_strategy (V112)
--   settlement_period_strategy -> final_settlement_period_strategy (V112)
--
-- and adds a 4th, brand-new axis:
--
--   retroactive_settlement_period_strategy — the window whose close triggers
--       a retroactive catch-up settlement. Backfilled from the just-renamed
--       final_settlement_period_strategy (not a hardcoded default) to
--       reproduce today's actual behavior: the retroactive top-up already
--       fires together with final settlement (CommissionRetroactiveTopUpService,
--       untouched by this migration).
--
-- Also adds the amount-basis pair (`basis` + `threshold_amount` +
-- `threshold_amount_currency_id`), letting a tier qualify by collected
-- amount instead of new-subscriber count — same >= threshold semantics as
-- `threshold_count`, mirroring `min_amount`/`min_amount_currency_id` on
-- collection_commission_tiers (V144) and `flat_amount_currency_id` on this
-- same table (V120). `basis` defaults to COUNT (backward compatible: every
-- existing tier keeps qualifying by threshold_count).
--
-- Anchors (day-of-week 1-7 for WEEKLY/BIWEEKLY, day-of-month 1-31 for
-- MONTHLY+) are added nullable for all 4 axes — interpretation is phase 2
-- (PeriodStrategies), not this migration.
-- ============================================================================

ALTER TABLE commission_tiers
    RENAME COLUMN period_strategy TO accrual_period_strategy;
ALTER TABLE commission_tiers
    RENAME CONSTRAINT chk_commission_tiers_period TO chk_commission_tiers_accrual_period_strategy;

ALTER TABLE commission_tiers
    RENAME COLUMN payout_period_strategy TO partial_settlement_period_strategy;
ALTER TABLE commission_tiers
    RENAME CONSTRAINT chk_commission_tiers_payout_period_strategy TO chk_commission_tiers_partial_settlement_period_strategy;

ALTER TABLE commission_tiers
    RENAME COLUMN settlement_period_strategy TO final_settlement_period_strategy;
ALTER TABLE commission_tiers
    RENAME CONSTRAINT chk_commission_tiers_settlement_period_strategy TO chk_commission_tiers_final_settlement_period_strategy;

-- New retroactive axis — backfilled from the (already renamed) final settlement
-- column so the UPDATE below is a true no-op vs. today's actual behavior.
ALTER TABLE commission_tiers
    ADD COLUMN retroactive_settlement_period_strategy VARCHAR(20);

UPDATE commission_tiers
SET retroactive_settlement_period_strategy = final_settlement_period_strategy
WHERE retroactive_settlement_period_strategy IS NULL;

ALTER TABLE commission_tiers
    ALTER COLUMN retroactive_settlement_period_strategy SET NOT NULL,
    ALTER COLUMN retroactive_settlement_period_strategy SET DEFAULT 'MONTHLY';

ALTER TABLE commission_tiers
    ADD CONSTRAINT chk_commission_tiers_retroactive_settlement_period_strategy
        CHECK (retroactive_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));

ALTER TABLE commission_tiers
    ADD COLUMN accrual_period_anchor               SMALLINT,
    ADD COLUMN partial_settlement_period_anchor     SMALLINT,
    ADD COLUMN final_settlement_period_anchor       SMALLINT,
    ADD COLUMN retroactive_settlement_period_anchor SMALLINT;

ALTER TABLE commission_tiers
    ADD CONSTRAINT chk_commission_tiers_accrual_period_anchor_range
        CHECK (accrual_period_anchor IS NULL OR accrual_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_commission_tiers_partial_settlement_period_anchor_range
        CHECK (partial_settlement_period_anchor IS NULL OR partial_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_commission_tiers_final_settlement_period_anchor_range
        CHECK (final_settlement_period_anchor IS NULL OR final_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_commission_tiers_retroactive_settlement_period_anchor_range
        CHECK (retroactive_settlement_period_anchor IS NULL OR retroactive_settlement_period_anchor BETWEEN 1 AND 31);

-- ─── Amount basis (new axis) ────────────────────────────────────────────────
ALTER TABLE commission_tiers
    ADD COLUMN basis VARCHAR(10) NOT NULL DEFAULT 'COUNT',
    ADD COLUMN threshold_amount NUMERIC(14,2),
    ADD COLUMN threshold_amount_currency_id BIGINT REFERENCES currencies (currencies_id);

ALTER TABLE commission_tiers
    ADD CONSTRAINT chk_commission_tiers_basis
        CHECK (basis IN ('COUNT', 'AMOUNT')),
    ADD CONSTRAINT chk_commission_tiers_basis_field
        CHECK ((basis = 'COUNT'  AND threshold_amount IS NULL AND threshold_amount_currency_id IS NULL)
            OR (basis = 'AMOUNT' AND threshold_amount IS NOT NULL AND threshold_amount_currency_id IS NOT NULL));


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM commission_tiers
        WHERE accrual_period_strategy IS NULL
           OR partial_settlement_period_strategy IS NULL
           OR final_settlement_period_strategy IS NULL
           OR retroactive_settlement_period_strategy IS NULL
           OR basis IS NULL
    ) THEN
        RAISE EXCEPTION 'V147: commission_tiers has NULL period strategy/basis after backfill';
    END IF;
END $$;
