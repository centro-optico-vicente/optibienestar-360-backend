SET search_path TO app, public;

-- ============================================================================
-- V126: closes the CollectionCommissionTier gap V124's header flagged as
-- "tracked separately" — adds an AMOUNT basis alongside the existing DAYS
-- bucket, the pct-XOR-flat reward shape already used by CommissionTier /
-- HierarchyOverrideTier, and the campaign anchor (campaign_id/starts_at/
-- ends_at) the other 3 rule tables got in V124/V125.
--
--   - basis: DAYS (default, backward compatible with every existing row) or
--     AMOUNT. max_days now only means something when basis=DAYS; the new
--     max_amount only means something when basis=AMOUNT — both nullable,
--     same ascending-bucket-cutoff semantics.
--   - flat_amount/flat_amount_currency_id: XOR against commission_pct, same
--     shape/constraint names as hierarchy_override_tiers (V102).
--   - campaign_id/starts_at/ends_at: same shape as commission_tiers /
--     commission_bonus_rules / hierarchy_override_tiers (V124).
-- ============================================================================

ALTER TABLE collection_commission_tiers
    ADD COLUMN basis                   VARCHAR(10) NOT NULL DEFAULT 'DAYS',
    ADD COLUMN max_amount              NUMERIC(14, 2),
    ADD COLUMN flat_amount             NUMERIC(10, 2),
    ADD COLUMN flat_amount_currency_id BIGINT REFERENCES currencies (currencies_id),
    ADD COLUMN campaign_id             BIGINT REFERENCES campaigns (campaigns_id),
    ADD COLUMN starts_at               TIMESTAMPTZ,
    ADD COLUMN ends_at                 TIMESTAMPTZ;

-- Existing rows are all days-based buckets — explicit backfill for clarity
-- even though DEFAULT 'DAYS' already covers them.
UPDATE collection_commission_tiers SET basis = 'DAYS' WHERE basis IS NULL;

-- max_days was NOT NULL (V44) — only meaningful now when basis=DAYS.
ALTER TABLE collection_commission_tiers
    ALTER COLUMN max_days DROP NOT NULL;

ALTER TABLE collection_commission_tiers
    ADD CONSTRAINT chk_collection_commission_tiers_basis
        CHECK (basis IN ('DAYS', 'AMOUNT')),
    ADD CONSTRAINT chk_collection_commission_tiers_basis_field
        CHECK ((basis = 'DAYS' AND max_days IS NOT NULL AND max_amount IS NULL)
            OR (basis = 'AMOUNT' AND max_amount IS NOT NULL AND max_days IS NULL)),
    -- Exactly one of pct / flat — same shape as hierarchy_override_tiers.
    ADD CONSTRAINT chk_collection_commission_tiers_pct_xor_flat
        CHECK ((commission_pct IS NOT NULL) <> (flat_amount IS NOT NULL)),
    ADD CONSTRAINT chk_collection_commission_tiers_flat_needs_currency
        CHECK (flat_amount IS NULL OR flat_amount_currency_id IS NOT NULL);

-- commission_pct was NOT NULL (V44) — now optional, the flat_amount side of the XOR.
ALTER TABLE collection_commission_tiers
    ALTER COLUMN commission_pct DROP NOT NULL;

CREATE INDEX idx_collection_commission_tiers_campaign ON collection_commission_tiers (campaign_id) WHERE campaign_id IS NOT NULL;
