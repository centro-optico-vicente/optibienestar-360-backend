SET search_path TO app, public;

-- ============================================================================
-- V144: flips the AMOUNT basis of collection_commission_tiers (V126) from a
-- ceiling ("applies when payment <= max_amount", smallest-qualifying-bucket)
-- to a minimum threshold ("applies when collected >= min_amount",
-- highest-qualifying-bucket) — same >= threshold semantics already used by
-- commission_tiers/hierarchy_override_tiers thresholds and
-- commission_bonus_rules.threshold_amount (V141). Product decision (hub chat
-- 2026-09-23): a bigger collected amount should unlock a MORE generous
-- bucket, not a smaller one.
--
-- max_amount -> min_amount (rename, same column/semantics owner) plus a
-- reference currency (min_amount_currency_id), mirroring
-- flat_amount/flat_amount_currency_id on this same table and
-- commission_bonus_rules.threshold_currency (V141) — the engine converts the
-- collected amount into this currency before comparing (CommissionService).
-- ============================================================================

ALTER TABLE collection_commission_tiers
    RENAME COLUMN max_amount TO min_amount;

ALTER TABLE collection_commission_tiers
    ADD COLUMN min_amount_currency_id BIGINT REFERENCES currencies (currencies_id);

-- Replaces V126's basis-field CHECK: AMOUNT rows now also require their
-- reference currency (same pattern as chk_collection_commission_tiers_flat_needs_currency).
ALTER TABLE collection_commission_tiers
    DROP CONSTRAINT chk_collection_commission_tiers_basis_field,
    ADD CONSTRAINT chk_collection_commission_tiers_basis_field
        CHECK ((basis = 'DAYS' AND max_days IS NOT NULL AND min_amount IS NULL AND min_amount_currency_id IS NULL)
            OR (basis = 'AMOUNT' AND min_amount IS NOT NULL AND min_amount_currency_id IS NOT NULL AND max_days IS NULL));
