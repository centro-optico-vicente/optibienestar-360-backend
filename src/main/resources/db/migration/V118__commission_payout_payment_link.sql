SET search_path TO app, public;

-- ============================================================================
-- V118: commissions / promoter_hierarchy_overrides / commission_retroactive_
-- topups gain payout_payment_id — the real FK to the `payments` row (direction
-- OUT) that pays them, per the hub plan
-- ".ai/plans/2026-09-17-payments-unification-plan.md" (decision #3).
--
-- Named `payout_payment_id`, not `payment_id`: `commissions.payment_id`
-- already exists (V26) and means something different — the IN payment that
-- TRIGGERED the commission (the member's membership fee payment). Reusing
-- that name for the OUT payout would collide two unrelated FKs under one
-- column. `payout_payment_id` is the payout counterpart of the existing
-- `payout_reference` free-text column.
--
-- This migration is schema-only: it adds the new nullable FK next to the
-- existing `payout_reference`/`paid_at` columns without touching them or the
-- Java entities/services that still read `payout_reference` today
-- (CommissionPayoutService et al.) — that refactor (decision #3's "coordinate
-- with the exchange_rate_at_paid snapshot") is explicitly a follow-up
-- session, not part of this schema-only pass. `payout_reference` stays until
-- that refactor lands and backfills payout_payment_id for any already-PAID
-- rows (plan §"Histórico ya PAID").
-- ============================================================================

ALTER TABLE commissions
    ADD COLUMN payout_payment_id BIGINT REFERENCES payments (payments_id);
CREATE INDEX idx_commissions_payout_payment ON commissions (payout_payment_id);

ALTER TABLE promoter_hierarchy_overrides
    ADD COLUMN payout_payment_id BIGINT REFERENCES payments (payments_id);
CREATE INDEX idx_promoter_hierarchy_overrides_payout_payment ON promoter_hierarchy_overrides (payout_payment_id);

ALTER TABLE commission_retroactive_topups
    ADD COLUMN payout_payment_id BIGINT REFERENCES payments (payments_id);
CREATE INDEX idx_commission_retroactive_topups_payout_payment ON commission_retroactive_topups (payout_payment_id);
