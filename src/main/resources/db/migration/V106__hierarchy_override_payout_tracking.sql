SET search_path TO app, public;

-- ============================================================================
-- V106: adds payout tracking to promoter_hierarchy_overrides — V102 shipped
-- earned_at/voided_at/void_reason but never payout_reference/paid_at, which
-- CommissionPayoutService (V105, PR4) needs to mark an override PAID as part
-- of the same period-close batch that pays direct commissions and
-- retroactive top-ups, mirroring commissions.payout_reference/paid_at (V26).
-- ============================================================================

ALTER TABLE promoter_hierarchy_overrides
    ADD COLUMN payout_reference VARCHAR(120),
    ADD COLUMN paid_at          TIMESTAMPTZ;
