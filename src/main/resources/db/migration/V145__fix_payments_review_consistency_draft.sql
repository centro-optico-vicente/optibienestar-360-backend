SET search_path TO app, public;

-- V129 added DRAFT to payments_status_check but left chk_payments_review_consistency
-- pinned to the old PENDING/APPROVED/REJECTED set, so inserting a DRAFT payment
-- violates the constraint (it matches neither branch). Mirror the fix already
-- applied to chk_payment_lines_review_consistency in V129.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_review_consistency;
ALTER TABLE payments ADD CONSTRAINT chk_payments_review_consistency CHECK (
    (status IN ('DRAFT', 'PENDING')
        AND reviewed_at IS NULL
        AND reviewed_by IS NULL)
    OR
    (status IN ('APPROVED', 'REJECTED')
        AND reviewed_at IS NOT NULL
        AND reviewed_by IS NOT NULL)
);
