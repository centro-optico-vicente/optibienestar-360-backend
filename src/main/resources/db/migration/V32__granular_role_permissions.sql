SET search_path TO app, public;

-- V32: split role management into granular permissions, and fix the two
-- misleading permissions in the USERS domain.
--
-- Problems this fixes:
--   1. ROLE_PERMISSION_EDIT was a single key guarding ALL role endpoints
--      (create, rename, delete AND assign-permissions), while its description
--      only mentioned assigning permissions. There was no way to let someone
--      edit a role without also letting them delete it.
--   2. USER_CHANGE_ROLE ("Asignar o revocar roles de usuario") never guarded
--      anything about user roles — assigning roles to a user goes through
--      USER_UPDATE. Its only real use is AdminCatalogsController.WRITE_AUTH,
--      i.e. it was a de-facto "only SYSTEM may write catalogs" marker. The name
--      invited granting it to ADMINISTRADOR and silently handing over catalog
--      writes.
--
-- After this migration ROLE_PERMISSION_EDIT means exactly what it always said:
-- assign permissions to a role.


-- 1. Catalogs finally get their own domain (the AdminCatalogsController javadoc
--    flagged "no dedicated CATALOG_* permissions exist in V6 seed yet").
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('CATALOGS', 'Catálogos', 'i-lucide-database',
        'Catálogos maestros de referencia (países, géneros, especialidades, etc.)', 110)
ON CONFLICT (code) DO NOTHING;


-- 2. Rename the misnamed USER_CHANGE_ROLE -> CATALOG_WRITE and move it to its
--    real domain. Renaming in place keeps every existing grant (role_permissions
--    rows are by id), so SYSTEM keeps catalog write access and nobody else gains it.
UPDATE permissions
SET name        = 'CATALOG_WRITE',
    description = 'Crear, editar y eliminar catálogos maestros',
    domain_id   = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOGS')
WHERE name = 'USER_CHANGE_ROLE';


-- 3. Granular role-management permissions. The V30 trigger auto-grants each of
--    these to the SYSTEM role on insert, so SYSTEM needs no explicit grant here.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ROLE_VIEW',   'USERS', 'Ver los roles del sistema'),
    ('ROLE_CREATE', 'USERS', 'Crear nuevos roles'),
    ('ROLE_UPDATE', 'USERS', 'Editar el nombre y la descripción de un rol'),
    ('ROLE_DELETE', 'USERS', 'Eliminar roles')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- 4a. Preserve read access: listing roles moves from USER_VIEW_ALL to ROLE_VIEW,
--     so every role that can already list roles keeps that ability. This matters
--     beyond the Roles screen — the Users screen loads the role list to populate
--     its role selector.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, (SELECT permissions_id FROM permissions WHERE name = 'ROLE_VIEW')
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
WHERE p.name = 'USER_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 4b. Preserve write access: whoever already had full role management
--     (ROLE_PERMISSION_EDIT) keeps create/update/delete as separate keys.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
         CROSS JOIN permissions np
WHERE p.name = 'ROLE_PERMISSION_EDIT'
  AND np.name IN ('ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 5. ROLE_PERMISSION_EDIT now guards only the permission-assignment endpoints,
--    so its original wording is finally accurate — just tighten it.
UPDATE permissions
SET description = 'Asignar permisos a un rol'
WHERE name = 'ROLE_PERMISSION_EDIT';


-- 6. Fail loudly on any name mismatch instead of migrating silently.
DO $$
DECLARE
    missing text;
BEGIN
    IF EXISTS (SELECT 1 FROM permissions WHERE name = 'USER_CHANGE_ROLE') THEN
        RAISE EXCEPTION 'V32: USER_CHANGE_ROLE still exists — rename to CATALOG_WRITE did not apply';
    END IF;

    SELECT string_agg(n, ', ') INTO missing
    FROM (VALUES ('ROLE_VIEW'), ('ROLE_CREATE'), ('ROLE_UPDATE'), ('ROLE_DELETE'), ('CATALOG_WRITE')) AS v(n)
    WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = v.n);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V32: expected permissions missing after migration: %', missing;
    END IF;

    -- ADMINISTRADOR held ROLE_PERMISSION_EDIT (V31), so it must come out of this
    -- migration with the full granular set.
    SELECT string_agg(n, ', ') INTO missing
    FROM (VALUES ('ROLE_VIEW'), ('ROLE_CREATE'), ('ROLE_UPDATE'), ('ROLE_DELETE')) AS v(n)
    WHERE NOT EXISTS (SELECT 1
                      FROM role_permissions rp
                               JOIN roles r ON r.roles_id = rp.role_id
                               JOIN permissions p ON p.permissions_id = rp.permission_id
                      WHERE r.name = 'ADMINISTRADOR' AND p.name = v.n);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V32: ADMINISTRADOR did not inherit granular role permissions: %', missing;
    END IF;
END $$;
