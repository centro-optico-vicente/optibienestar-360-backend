-- ====================================================
-- V216__fix_advisor_role_permission_grants.sql
--
-- V185 renamed the role PROMOTOR to ASESOR, but V201, V202 and V205 still
-- granted their new permissions to 'PROMOTOR'. That role no longer exists,
-- so the INSERT ... SELECT matched nothing and advisors never received:
--
--   V201: COLLECTION_VIEW_OWN, COLLECTION_CREATE_OWN, COLLECTION_DELETE_OWN,
--         MEMBER_ADVISOR_VIEW_OWN, PROMOTER_PORTFOLIO_CREATE
--   V202: MEMBER_VIEW_OWN
--   V205: COLLECTION_CLAIM_CREATE_OWN
--
-- Grant them to ASESOR now. ON CONFLICT keeps it idempotent (an admin may
-- have added some by hand from the Roles screen).
-- ====================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ASESOR'
  AND p.name IN ('COLLECTION_VIEW_OWN', 'COLLECTION_CREATE_OWN', 'COLLECTION_DELETE_OWN',
                 'MEMBER_ADVISOR_VIEW_OWN', 'PROMOTER_PORTFOLIO_CREATE',
                 'MEMBER_VIEW_OWN', 'COLLECTION_CLAIM_CREATE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Fail loudly if the role exists but any grant is still missing.
DO $$
DECLARE
    missing TEXT;
BEGIN
    SELECT string_agg(want.name, ', ') INTO missing
    FROM (VALUES ('COLLECTION_VIEW_OWN'), ('COLLECTION_CREATE_OWN'), ('COLLECTION_DELETE_OWN'),
                 ('MEMBER_ADVISOR_VIEW_OWN'), ('PROMOTER_PORTFOLIO_CREATE'),
                 ('MEMBER_VIEW_OWN'), ('COLLECTION_CLAIM_CREATE_OWN')) AS want(name)
    WHERE EXISTS (SELECT 1 FROM roles WHERE name = 'ASESOR')
      AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ASESOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id
        WHERE p.name = want.name);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V216: ASESOR is still missing %', missing;
    END IF;
END $$;
