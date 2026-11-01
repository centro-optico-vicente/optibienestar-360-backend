SET search_path TO app, public;

-- ============================================================================
-- V221: granular permission to VIEW the compliance of the memberships of a plan
-- with the plan's term rule (hub ADR 0034). Applying the rule reuses
-- MEMBERSHIP_TERM_UPDATE (V220).
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBERSHIP_TERM_VIEW_ALL', 'MEMBERSHIPS', 'Ver qué membresías de un plan cumplen la regla de vigencia del plan')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR') AND p.name = 'MEMBERSHIP_TERM_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'MEMBERSHIP_TERM_VIEW_ALL') THEN
        RAISE EXCEPTION 'V221: MEMBERSHIP_TERM_VIEW_ALL was not created';
    END IF;
END $$;
