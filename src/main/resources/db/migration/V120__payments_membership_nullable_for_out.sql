SET search_path TO app, public;

-- ============================================================================
-- V120: payments.membership_id becomes nullable — a gap V117 left open.
--
-- V117 added `direction` (IN/OUT) to payments but left `membership_id` as the
-- original V23 `NOT NULL` — correct for `IN` (a collection always settles a
-- membership fee) but wrong for `OUT`: a commission-payout payment
-- (CommissionPayoutService, hub plan
-- ".ai/plans/2026-09-17-payments-unification-plan.md") has no membership at
-- all — it pays a promoter, not an affiliate's fee. Every `OUT` row would
-- violate this constraint on insert.
--
-- `idx_payments_membership_date` (V23) stays as-is — a partial/NULL-inclusive
-- index over a now-nullable column still serves the same "per-membership
-- payment history" query for IN rows.
-- ============================================================================

ALTER TABLE payments ALTER COLUMN membership_id DROP NOT NULL;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_membership_by_direction CHECK (
        (direction = 'IN' AND membership_id IS NOT NULL)
        OR
        (direction = 'OUT' AND membership_id IS NULL)
    );
