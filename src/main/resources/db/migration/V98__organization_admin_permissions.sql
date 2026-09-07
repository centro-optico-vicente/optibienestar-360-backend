SET search_path TO app, public;

-- ============================================================
-- V98: admin CRUD for organizations (ADR 0015 §4, plan "CRUD admin de
-- Currency + ExchangeRate y país↔moneda oficial", último punto pendiente:
-- GET/PUT /v1/admin/organizations/me).
--
-- organizations is single-row today (V86 seed) — there is no "create" or
-- "list" surface, only read/update the one tenant row, so a single
-- ORGANIZATION_VIEW + ORGANIZATION_UPDATE pair is enough (no _CREATE/_DELETE:
-- neither operation makes sense on a singleton, and there is no admin UI
-- path today that would need them).
-- ============================================================

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('ORGANIZATION', 'Organización', 'i-lucide-building-2',
        'Datos de la organización/empresa (ADR 0015)', 121)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ORGANIZATION_VIEW',   'ORGANIZATION', 'Ver los datos de la organización'),
    ('ORGANIZATION_UPDATE', 'ORGANIZATION', 'Actualizar los datos de la organización')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- SYSTEM / ADMINISTRADOR: both new permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('ORGANIZATION_VIEW', 'ORGANIZATION_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permission_domains WHERE code = 'ORGANIZATION') THEN
        RAISE EXCEPTION 'V98: ORGANIZATION permission domain was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES ('ORGANIZATION_VIEW'), ('ORGANIZATION_UPDATE')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V98: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('ORGANIZATION_VIEW', 'ORGANIZATION_UPDATE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V98: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('ORGANIZATION_VIEW', 'ORGANIZATION_UPDATE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V98: ADMINISTRADOR did not receive the new permissions';
    END IF;
END $$;
