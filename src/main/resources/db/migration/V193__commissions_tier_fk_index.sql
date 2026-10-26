-- V193 — Index the commissions.commission_tier_id foreign key.
--
-- The FK itself (fk_commissions_tier -> commission_tiers, default NO ACTION, i.e. a
-- tier referenced by any commission cannot be hard-deleted) was already added and
-- validated by V42, so there is nothing to backfill or re-validate here. What was
-- never created is a supporting index: PostgreSQL does not index FK columns
-- automatically, so every DELETE on commission_tiers (and the usage check in
-- CommissionTiersService.countUsages) scanned the whole commissions table.
--
-- Partial (NOT NULL only) like idx_commissions_collection_tier (V48): the column is
-- NULL for non-tier commissions (e.g. competitive/flat rules), which are skipped.
CREATE INDEX IF NOT EXISTS idx_commissions_tier
    ON commissions (commission_tier_id)
    WHERE commission_tier_id IS NOT NULL;
