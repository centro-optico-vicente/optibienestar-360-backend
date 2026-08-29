SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V81: granular permissions for the entity_config admin CRUD (renamed from
-- audit_entity_config by V80) — VIEW/CREATE/UPDATE/DELETE, same split as
-- ALLY_VIEW_ALL/ALLY_CREATE/ALLY_UPDATE/ALLY_DELETE. Deliberately SYSTEM-only
-- for now — none get an explicit role_permissions row for any role; SYSTEM
-- receives all four only via the V30 auto-grant trigger. Widen later (grant
-- to ADMINISTRADOR) once the screen has been used long enough to trust wider
-- access.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ENTITY_CONFIG_VIEW',   'AUDIT', 'Ver la configuración de entidades: auditoría, orden predeterminado (solo SYSTEM)'),
    ('ENTITY_CONFIG_CREATE', 'AUDIT', 'Registrar la configuración de una entidad nueva (solo SYSTEM)'),
    ('ENTITY_CONFIG_UPDATE', 'AUDIT', 'Editar la configuración de entidades: auditoría, orden predeterminado (solo SYSTEM)'),
    ('ENTITY_CONFIG_DELETE', 'AUDIT', 'Eliminar la configuración de una entidad (solo SYSTEM)')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY['ENTITY_CONFIG_VIEW', 'ENTITY_CONFIG_CREATE', 'ENTITY_CONFIG_UPDATE', 'ENTITY_CONFIG_DELETE']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V81: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V81: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;
    END LOOP;
END $$;
