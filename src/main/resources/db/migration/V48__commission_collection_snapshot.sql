SET search_path TO app, public;

-- V48: collection-commission snapshot on `commissions` (vertical-8 Ítem B).
--
-- Populated by CommissionService when a MONTHLY commission is priced via the
-- collection-commission engine (days-late → collection_commission_tiers,
-- V44) instead of the plan/volume commission_tiers. NULL for INSCRIPTION
-- rows and for MONTHLY rows where no collection tier was configured/applicable
-- (the engine falls back to the volume-tier calculation in that case).

ALTER TABLE commissions
    ADD COLUMN collection_days     INT    NULL
        CONSTRAINT chk_commissions_collection_days CHECK (collection_days >= 0),
    ADD COLUMN collection_tier_id  BIGINT NULL
        REFERENCES collection_commission_tiers (collection_commission_tiers_id);

COMMENT ON COLUMN commissions.collection_days IS
    'Days late the recurring payment was collected (payment_date - scheduled collection date), when priced by the collection-commission engine.';
COMMENT ON COLUMN commissions.collection_tier_id IS
    'FK to the collection_commission_tiers bucket applied, when priced by the collection-commission engine.';

CREATE INDEX idx_commissions_collection_tier
    ON commissions (collection_tier_id) WHERE collection_tier_id IS NOT NULL;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'commissions' AND column_name = 'collection_days'
    ) THEN
        RAISE EXCEPTION 'V48: commissions.collection_days was not created';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'commissions' AND column_name = 'collection_tier_id'
    ) THEN
        RAISE EXCEPTION 'V48: commissions.collection_tier_id was not created';
    END IF;
END $$;
