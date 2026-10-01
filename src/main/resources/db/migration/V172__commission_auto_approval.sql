SET search_path TO app, public;

-- ============================================================================
-- V172: company-wide setting to auto-approve "regular" commissions (no
-- campaign attribution) straight to APPROVED at calculation time, skipping
-- the gerencia comercial queue (V107) for the common case — see
-- CommissionService#qualifiesForAutoApproval. A campaign-linked commission,
-- or anything produced by the retroactive/re-rating services, always stays
-- PENDING (manual review) regardless of this flag.
--
-- Both new columns default false/0 — no behavior change until an admin
-- turns the organization setting on.
-- ============================================================================

ALTER TABLE organizations
    ADD COLUMN auto_approve_commissions BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE commissions
    ADD COLUMN auto_approved BOOLEAN NOT NULL DEFAULT false;

-- V107's chk_commissions_approved_consistency required a human reviewer
-- (approved_by_user_id NOT NULL) whenever status=APPROVED — true until now,
-- but a system auto-approval has no human actor to record. Widen it to also
-- accept auto_approved=true with a null approver, keeping the human-reviewer
-- requirement for every row the setting didn't touch.
ALTER TABLE commissions
    DROP CONSTRAINT chk_commissions_approved_consistency;

ALTER TABLE commissions
    ADD CONSTRAINT chk_commissions_approved_consistency
        CHECK (status <> 'APPROVED' OR auto_approved OR (approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'organizations' AND column_name = 'auto_approve_commissions'
    ) THEN
        RAISE EXCEPTION 'V172: organizations.auto_approve_commissions was not created';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'commissions' AND column_name = 'auto_approved'
    ) THEN
        RAISE EXCEPTION 'V172: commissions.auto_approved was not created';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'commissions'::regclass AND conname = 'chk_commissions_approved_consistency'
    ) THEN
        RAISE EXCEPTION 'V172: chk_commissions_approved_consistency was not recreated';
    END IF;
END $$;
