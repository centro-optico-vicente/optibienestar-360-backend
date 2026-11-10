SET search_path TO app, public;

-- ============================================================================
-- V235: assigned advisor on allies ("who brought the ally into OptiBienestar
-- 360"). The column already exists (allies.captured_by_promoter_id, V179); what
-- is new is who may change it and the advisor's own read-only list.
--
--   ALLY_ASSIGN_PROMOTER     staff: assign / change / clear the advisor of an
--                            ally (PUT /v1/admin/allies/{uuid}/advisor). Granted
--                            to every role that can already edit an ally
--                            (ALLY_UPDATE), the same pattern as V214.
--                            Named like MEMBER_ASSIGN_PROMOTER (V35).
--   PROMOTER_ALLY_VIEW_OWN   advisor portal: read-only list of the allies the
--                            caller captured (GET /v1/me/advisor/allies).
--                            Granted to ASESOR (SYSTEM through the V30 trigger).
-- Editing the advisor through the generic ally update (ALLY_UPDATE) stops
-- working: the dedicated permission is the only way to change it afterwards.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ALLY_ASSIGN_PROMOTER',   'ALLIES',    'Asignar o cambiar el asesor que trajo a un aliado'),
    ('PROMOTER_ALLY_VIEW_OWN', 'PROMOTERS', 'Ver los aliados que el asesor propio captó (solo lectura)')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Staff: every role that can already edit an ally.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM permissions ep
JOIN role_permissions rp ON rp.permission_id = ep.permissions_id
JOIN permissions np ON np.name = 'ALLY_ASSIGN_PROMOTER'
WHERE ep.name = 'ALLY_UPDATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Advisor portal (SYSTEM listed explicitly as well).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ASESOR', 'SYSTEM')
  AND p.name = 'PROMOTER_ALLY_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions WHERE name IN ('ALLY_ASSIGN_PROMOTER', 'PROMOTER_ALLY_VIEW_OWN')) <> 2 THEN
        RAISE EXCEPTION 'V235: the ally advisor permissions were not created (ALLIES / PROMOTERS domain missing?)';
    END IF;
    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN permissions ep ON ep.permissions_id = rp.permission_id AND ep.name = 'ALLY_UPDATE'
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp2
            JOIN permissions np ON np.permissions_id = rp2.permission_id AND np.name = 'ALLY_ASSIGN_PROMOTER'
            WHERE rp2.role_id = rp.role_id)
    ) THEN
        RAISE EXCEPTION 'V235: a role that edits allies did not receive ALLY_ASSIGN_PROMOTER';
    END IF;
END $$;
