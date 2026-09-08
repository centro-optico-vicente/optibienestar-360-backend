SET search_path TO app, public;

-- ============================================================================
-- V107: commission approval workflow (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §4, PR5) — the
-- "simulación vs. aprobación vs. pago" gate. A calculated commission is a
-- simulation until gerencia comercial approves it; gerencia de
-- administración must only ever pay what was already approved.
--
-- Only the DIRECT commission carries approval state — hierarchy overrides
-- (V102) and retroactive top-ups (V105) have no approval column of their
-- own; they inherit the fate of the commission that funded them (voided in
-- cascade on rejection — see CommissionApprovalService), never approved
-- independently.
--
-- `status` gains APPROVED/REJECTED alongside the existing PENDING/PAID/
-- VOIDED/DISPUTED (V26). `approved_by_user_id`/`approved_at` are shared by
-- both outcomes (they record who reviewed it and when, regardless of
-- verdict); `rejection_reason` is required only for REJECTED, same
-- "outcome ⇒ required fields" pattern the V26 paid/voided CHECKs already
-- established.
-- ============================================================================

ALTER TABLE commissions
    ADD COLUMN approved_by_user_id BIGINT REFERENCES users (users_id),
    ADD COLUMN approved_at         TIMESTAMPTZ,
    ADD COLUMN rejection_reason    TEXT;

ALTER TABLE commissions
    DROP CONSTRAINT commissions_status_check;

ALTER TABLE commissions
    ADD CONSTRAINT commissions_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID', 'VOIDED', 'DISPUTED'));

ALTER TABLE commissions
    ADD CONSTRAINT chk_commissions_approved_consistency
        CHECK (status <> 'APPROVED' OR (approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)),
    ADD CONSTRAINT chk_commissions_rejected_consistency
        CHECK (status <> 'REJECTED' OR (approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL AND rejection_reason IS NOT NULL));

CREATE INDEX idx_commissions_status_approval
    ON commissions (status)
    WHERE status IN ('PENDING', 'APPROVED');


-- ─── Permission: COMMISSION_APPROVE (gerencia comercial) ────────────────────
-- Distinct from COMMISSION_PAYOUT (gerencia de administración) — separation
-- of functions explicitly requested by the business (hub plan §4).
INSERT INTO permissions (name, domain_id, description)
SELECT 'COMMISSION_APPROVE', pd.permission_domains_id, 'Aprobar o rechazar comisiones calculadas antes de su pago'
FROM permission_domains pd
WHERE pd.code = 'COMMISSIONS';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name = 'COMMISSION_APPROVE'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'COMMISSION_APPROVE') THEN
        RAISE EXCEPTION 'V107: COMMISSION_APPROVE was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'COMMISSION_APPROVE'
    ) THEN
        RAISE EXCEPTION 'V107: SYSTEM did not receive COMMISSION_APPROVE (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'COMMISSION_APPROVE'
    ) THEN
        RAISE EXCEPTION 'V107: ADMINISTRADOR did not receive COMMISSION_APPROVE';
    END IF;
END $$;
