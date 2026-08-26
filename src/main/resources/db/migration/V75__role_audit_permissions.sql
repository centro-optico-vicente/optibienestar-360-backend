SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V75: role-level audit permissions, closing a gap left by V66/V71. Those two
-- migrations added <DOMAIN>_AUDIT_VIEW / <DOMAIN>_AUDIT_RESTORE /
-- <DOMAIN>_REPORT_AUDIT_VIEW per permission domain (USERS, MEMBERS, ...), but
-- "role" sits inside the USERS domain alongside "user" (V32) and never got its
-- own triple — even though RoleService already fires @Auditable(entity="role"/
-- "user_role", ...) on every create/update/delete/assign/revoke. Without this,
-- role audit history was only reachable via the blanket AUDIT_VIEW_ALL /
-- REPORT_AUDIT_VIEW_ALL permissions.
--
-- Named ROLE_* (not USER_*) so it can be granted independently of user-level
-- audit access, same reasoning as V73's per-catalog-entity split within
-- CATALOGS. ROLE_AUDIT_RESTORE mirrors every other domain's *_AUDIT_RESTORE:
-- permission created, restore functionality not implemented (V66).
--
-- SYSTEM receives every new permission automatically via the V30 trigger.
-- ADMINISTRADOR gets explicit grants for the same reason as V66/V71.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ROLE_AUDIT_VIEW',        'USERS', 'Ver el historial de cambios de un rol'),
    ('ROLE_AUDIT_RESTORE',     'USERS', 'Restaurar un rol a un punto de su historial'),
    ('ROLE_REPORT_AUDIT_VIEW', 'USERS', 'Ver el historial de reportes generados de roles')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: full access to the new role-audit permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('ROLE_AUDIT_VIEW', 'ROLE_AUDIT_RESTORE', 'ROLE_REPORT_AUDIT_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY[
        'ROLE_AUDIT_VIEW', 'ROLE_AUDIT_RESTORE', 'ROLE_REPORT_AUDIT_VIEW'
    ]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V75: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V75: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V75: ADMINISTRADOR did not receive %', new_permission;
        END IF;
    END LOOP;
END $$;
