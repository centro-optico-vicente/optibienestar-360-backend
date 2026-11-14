SET search_path TO app, public;

-- ============================================================================
-- V253: OPERADOR reviews affiliation requests by default
--
-- V190 left the review of affiliation requests to SYSTEM / ADMINISTRADOR and
-- said "OPERADOR gets none by default: it is assigned from Roles". Operations
-- is who reviews the requests the advisors send (assisted affiliation, hub
-- ADR 0037 §5: the advisor fills the draft and sends it; on approval the
-- person gets the "create your password" link), so OPERADOR now receives the
-- review side by default:
--
--   AFFILIATION_REQUEST_VIEW_ALL   list / detail / proof of payment
--   AFFILIATION_REQUEST_UPDATE     correct a PENDING request
--   AFFILIATION_REQUEST_APPROVE    approve (creates user, member, membership)
--   AFFILIATION_REQUEST_REJECT     reject with a reason
--
-- OPERADOR_MEDICO is left as is: it does not mirror OPERADOR (no campaigns,
-- collections, company accounts…) and still gets these from Roles if needed.
-- Retention of abandoned drafts and changing the advisor stay with the roles
-- that already hold them.
-- ============================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'OPERADOR'
  AND p.name IN ('AFFILIATION_REQUEST_VIEW_ALL', 'AFFILIATION_REQUEST_UPDATE',
                 'AFFILIATION_REQUEST_APPROVE', 'AFFILIATION_REQUEST_REJECT')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM roles WHERE name = 'OPERADOR') AND (
        SELECT COUNT(*) FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'OPERADOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id
        WHERE p.name IN ('AFFILIATION_REQUEST_VIEW_ALL', 'AFFILIATION_REQUEST_UPDATE',
                         'AFFILIATION_REQUEST_APPROVE', 'AFFILIATION_REQUEST_REJECT')
    ) <> 4 THEN
        RAISE EXCEPTION 'V253: OPERADOR did not receive the affiliation request review permissions';
    END IF;
END $$;
