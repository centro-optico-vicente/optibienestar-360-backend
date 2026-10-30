SET search_path TO app, public;

-- ============================================================================
-- V202: "Mi carnet" and "Mis consumos" belong to the person, not to the AFILIADO
-- role: an advisor or an ally is also a person who may be (or become) an
-- affiliate. MEMBER_VIEW_OWN only ever exposes the caller's own member record
-- (/v1/me/member, /v1/me/digital-card, /v1/me/usage-history, /v1/me/family), and
-- answers the usual empty state when the caller is not enrolled, so granting it
-- to PROMOTOR and ALIADO leaks nothing. Hub plan
-- ".ai/plans/2026-10-29-payments-without-affiliation.md".
-- ============================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('PROMOTOR', 'ALIADO')
  AND p.name = 'MEMBER_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM roles r
        WHERE r.name IN ('PROMOTOR', 'ALIADO')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'MEMBER_VIEW_OWN'
            WHERE rp.role_id = r.roles_id)
    ) THEN
        RAISE EXCEPTION 'V202: PROMOTOR/ALIADO did not receive MEMBER_VIEW_OWN';
    END IF;
END $$;
