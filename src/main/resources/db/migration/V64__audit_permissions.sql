SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V64: permission catalog for the audit admin surface (spec 07-audit.md
-- §Endpoints admin, §Permisos granulares). Domain-agnostic — these two govern
-- the cross-entity admin screens (login history, audit config), as opposed
-- to the per-domain <DOMAIN>_AUDIT_VIEW/_RESTORE/_REPORT_GENERATE permissions
-- added in V66.
--
-- SYSTEM receives both automatically via the V30 trigger. ADMINISTRADOR gets
-- an explicit grant because its V6 INSERT was a one-time snapshot, not a
-- trigger (same rationale as V54).
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('AUDIT', 'Auditoría', 'i-lucide-shield-check', 'Bitácora de accesos, cambios y configuración de auditoría', 110)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('AUDIT_VIEW_LOGIN',    'AUDIT', 'Ver la bitácora de intentos de acceso y sesiones'),
    ('AUDIT_MANAGE_CONFIG', 'AUDIT', 'Configurar el alcance de auditoría por entidad')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('AUDIT_VIEW_LOGIN', 'AUDIT_MANAGE_CONFIG')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY['AUDIT_VIEW_LOGIN', 'AUDIT_MANAGE_CONFIG']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V64: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V64: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V64: ADMINISTRADOR did not receive %', new_permission;
        END IF;
    END LOOP;
END $$;
