SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V74: split "assign/remove people" out of the resource-edit permissions.
--
-- Problem: managing which users belong to a role (`/v1/admin/roles/{uuid}/users`)
-- had no dedicated permission, and managing which staff belong to an ally
-- (`/v1/admin/allies/{uuid}/users`) was gated by ALLY_UPDATE — the same key
-- that guards editing the ally's own business data. That conflates "can edit
-- this ally record" with "can add/remove people from it".
--
-- This migration:
--   1. Adds ROLE_USERS_MANAGE (USERS domain) — assign/remove users on a role.
--      No backfill: nobody currently manages role membership through a
--      dedicated permission (SYSTEM gets it automatically via the V30 trigger).
--   2. Adds ALLY_USERS_MANAGE (ALLIES domain) — assign/remove ally staff.
--      Backfilled to every role that currently holds ALLY_UPDATE, since that
--      is the permission AdminAllyUsersController write endpoints were gated
--      by until now.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ROLE_USERS_MANAGE', 'USERS',  'Asignar/quitar usuarios de un rol'),
    ('ALLY_USERS_MANAGE', 'ALLIES', 'Asignar/quitar personal de un aliado')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- Backfill: every role that currently has ALLY_UPDATE also gets ALLY_USERS_MANAGE.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, (SELECT permissions_id FROM permissions WHERE name = 'ALLY_USERS_MANAGE')
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
WHERE p.name = 'ALLY_UPDATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    missing text;
BEGIN
    SELECT string_agg(n, ', ') INTO missing
    FROM (VALUES ('ROLE_USERS_MANAGE'), ('ALLY_USERS_MANAGE')) AS v(n)
    WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = v.n);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'V74: expected permissions missing after migration: %', missing;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id
                                    AND p.name IN ('ROLE_USERS_MANAGE', 'ALLY_USERS_MANAGE')
        GROUP BY r.roles_id
        HAVING COUNT(*) = 2
    ) THEN
        RAISE EXCEPTION 'V74: SYSTEM did not receive both new permissions (V30 trigger?)';
    END IF;

    -- Every role that had ALLY_UPDATE must now also have ALLY_USERS_MANAGE.
    IF EXISTS (
        SELECT 1
        FROM role_permissions rp
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'ALLY_UPDATE'
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp2
                     JOIN permissions p2 ON p2.permissions_id = rp2.permission_id AND p2.name = 'ALLY_USERS_MANAGE'
            WHERE rp2.role_id = rp.role_id
        )
    ) THEN
        RAISE EXCEPTION 'V74: not every ALLY_UPDATE role received ALLY_USERS_MANAGE';
    END IF;
END $$;
