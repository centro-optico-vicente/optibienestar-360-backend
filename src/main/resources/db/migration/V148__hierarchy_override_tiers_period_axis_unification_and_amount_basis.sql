SET search_path TO app, public;

-- ============================================================================
-- V148: hierarchy_override_tiers half of the settlement-frequency naming
-- unification (hub plan commission-frequency-currency-unification, Fase A) —
-- same treatment as V147 on commission_tiers. Renames:
--
--   period_strategy          -> accrual_period_strategy   (V102)
--   payout_period_strategy   -> partial_settlement_period_strategy (V112)
--   settlement_period_strategy -> final_settlement_period_strategy (V112)
--
-- adds the new retroactive_settlement_period_strategy axis (backfilled from
-- the just-renamed final_settlement_period_strategy, same no-op reasoning as
-- V147), 4 nullable anchors, and the amount-basis pair (`basis` +
-- `threshold_amount` + `threshold_amount_currency_id`) so a band can qualify
-- by team-collected amount instead of team-volume count — mirrors
-- commission_tiers (V147) exactly.
-- ============================================================================

ALTER TABLE hierarchy_override_tiers
    RENAME COLUMN period_strategy TO accrual_period_strategy;
ALTER TABLE hierarchy_override_tiers
    RENAME CONSTRAINT chk_hierarchy_override_tiers_period_strategy TO chk_hierarchy_override_tiers_accrual_period_strategy;

ALTER TABLE hierarchy_override_tiers
    RENAME COLUMN payout_period_strategy TO partial_settlement_period_strategy;
ALTER TABLE hierarchy_override_tiers
    RENAME CONSTRAINT chk_hierarchy_override_tiers_payout_period_strategy TO chk_hierarchy_override_tiers_partial_settlement_period_strategy;

ALTER TABLE hierarchy_override_tiers
    RENAME COLUMN settlement_period_strategy TO final_settlement_period_strategy;
ALTER TABLE hierarchy_override_tiers
    RENAME CONSTRAINT chk_hierarchy_override_tiers_settlement_period_strategy TO chk_hierarchy_override_tiers_final_settlement_period_strategy;

-- New retroactive axis — backfilled from the (already renamed) final settlement
-- column so the UPDATE below is a true no-op vs. today's actual behavior.
ALTER TABLE hierarchy_override_tiers
    ADD COLUMN retroactive_settlement_period_strategy VARCHAR(20);

UPDATE hierarchy_override_tiers
SET retroactive_settlement_period_strategy = final_settlement_period_strategy
WHERE retroactive_settlement_period_strategy IS NULL;

ALTER TABLE hierarchy_override_tiers
    ALTER COLUMN retroactive_settlement_period_strategy SET NOT NULL,
    ALTER COLUMN retroactive_settlement_period_strategy SET DEFAULT 'MONTHLY';

ALTER TABLE hierarchy_override_tiers
    ADD CONSTRAINT chk_hierarchy_override_tiers_retroactive_settlement_period_strategy
        CHECK (retroactive_settlement_period_strategy IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMIANNUAL', 'ANNUAL'));

ALTER TABLE hierarchy_override_tiers
    ADD COLUMN accrual_period_anchor               SMALLINT,
    ADD COLUMN partial_settlement_period_anchor     SMALLINT,
    ADD COLUMN final_settlement_period_anchor       SMALLINT,
    ADD COLUMN retroactive_settlement_period_anchor SMALLINT;

ALTER TABLE hierarchy_override_tiers
    ADD CONSTRAINT chk_hierarchy_override_tiers_accrual_period_anchor_range
        CHECK (accrual_period_anchor IS NULL OR accrual_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_hierarchy_override_tiers_partial_settlement_period_anchor_range
        CHECK (partial_settlement_period_anchor IS NULL OR partial_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_hierarchy_override_tiers_final_settlement_period_anchor_range
        CHECK (final_settlement_period_anchor IS NULL OR final_settlement_period_anchor BETWEEN 1 AND 31),
    ADD CONSTRAINT chk_hierarchy_override_tiers_retroactive_settlement_period_anchor_range
        CHECK (retroactive_settlement_period_anchor IS NULL OR retroactive_settlement_period_anchor BETWEEN 1 AND 31);

-- ─── Amount basis (new axis) ────────────────────────────────────────────────
ALTER TABLE hierarchy_override_tiers
    ADD COLUMN basis VARCHAR(10) NOT NULL DEFAULT 'COUNT',
    ADD COLUMN threshold_amount NUMERIC(14,2),
    ADD COLUMN threshold_amount_currency_id BIGINT REFERENCES currencies (currencies_id);

ALTER TABLE hierarchy_override_tiers
    ADD CONSTRAINT chk_hierarchy_override_tiers_basis
        CHECK (basis IN ('COUNT', 'AMOUNT')),
    ADD CONSTRAINT chk_hierarchy_override_tiers_basis_field
        CHECK ((basis = 'COUNT'  AND threshold_amount IS NULL AND threshold_amount_currency_id IS NULL)
            OR (basis = 'AMOUNT' AND threshold_amount IS NOT NULL AND threshold_amount_currency_id IS NOT NULL));


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM hierarchy_override_tiers
        WHERE accrual_period_strategy IS NULL
           OR partial_settlement_period_strategy IS NULL
           OR final_settlement_period_strategy IS NULL
           OR retroactive_settlement_period_strategy IS NULL
           OR basis IS NULL
    ) THEN
        RAISE EXCEPTION 'V148: hierarchy_override_tiers has NULL period strategy/basis after backfill';
    END IF;
END $$;
