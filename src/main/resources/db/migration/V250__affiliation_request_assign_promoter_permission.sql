SET search_path TO app, public;

-- ============================================================================
-- V250: who may change the advisor of an open affiliation request
-- (PUT /v1/admin/affiliation-requests/{uuid}/advisor, ADR LOC-0016 portfolio
-- distribution). Until now the advisor of a request came only from the
-- referral code or the assisted flow and could not be corrected.
--
--   AFFILIATION_REQUEST_ASSIGN_PROMOTER   staff: change the advisor of a DRAFT
--                                         or PENDING affiliation request.
--                                         Granted to every role that can
--                                         already reassign a member's advisor
--                                         (MEMBER_ASSIGN_PROMOTER, V35), the
--                                         same pattern as V235. Named like
--                                         MEMBER_ASSIGN_PROMOTER /
--                                         ALLY_ASSIGN_PROMOTER.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AFFILIATION_REQUEST_ASSIGN_PROMOTER', 'AFFILIATION_REQUESTS',
     'Cambiar el asesor de una solicitud de afiliación en borrador o pendiente')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Staff: every role that can already reassign a member's advisor.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM permissions ep
JOIN role_permissions rp ON rp.permission_id = ep.permissions_id
JOIN permissions np ON np.name = 'AFFILIATION_REQUEST_ASSIGN_PROMOTER'
WHERE ep.name = 'MEMBER_ASSIGN_PROMOTER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'AFFILIATION_REQUEST_ASSIGN_PROMOTER') THEN
        RAISE EXCEPTION 'V250: AFFILIATION_REQUEST_ASSIGN_PROMOTER was not created (AFFILIATION_REQUESTS domain missing?)';
    END IF;
    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN permissions ep ON ep.permissions_id = rp.permission_id AND ep.name = 'MEMBER_ASSIGN_PROMOTER'
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp2
            JOIN permissions np ON np.permissions_id = rp2.permission_id
                               AND np.name = 'AFFILIATION_REQUEST_ASSIGN_PROMOTER'
            WHERE rp2.role_id = rp.role_id)
    ) THEN
        RAISE EXCEPTION 'V250: a role that reassigns member advisors did not receive AFFILIATION_REQUEST_ASSIGN_PROMOTER';
    END IF;
END $$;
