SET search_path TO app, public;

-- V34: dedicated read permission for the admin catalog endpoints.
--
-- The GET endpoints of AdminCatalogsController were gated on USER_VIEW_ALL — a
-- USERS-domain permission borrowed for pragmatism (see the V33 note). That
-- coupled two unrelated domains: a catalog-manager role had to be granted
-- USER_VIEW_ALL just to read the lists, and anyone who could view users could
-- read every catalog. It also left a gap — the frontend opens a catalog screen
-- on that catalog's CATALOG_*_WRITE key (V33), but the read GET demanded
-- USER_VIEW_ALL, so a writer WITHOUT USER_VIEW_ALL could open a screen whose
-- list then answered 403.
--
-- This introduces CATALOG_VIEW_ALL and moves the reads onto it (controller
-- change ships in the same PR). The grants below make the switch
-- behavior-preserving on deploy: every role that can read catalogs today
-- (USER_VIEW_ALL holders) keeps that access, and every catalog writer gains the
-- matching read so the screens it can already open actually load. No role loses
-- catalog read; the only net effect is decoupling the permission from the USERS
-- domain (and closing the writer-without-reader 403).


-- 1. The read permission, in the CATALOGS domain (created by V32). The V30
--    trigger auto-grants it to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT 'CATALOG_VIEW_ALL', pd.permission_domains_id,
       'Ver los catálogos administrables (listar y ver detalle)'
FROM permission_domains pd
WHERE pd.code = 'CATALOGS';


-- 2. Behavior-preserving: every role that reads catalogs today through
--    USER_VIEW_ALL keeps reading them.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'USER_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name = 'CATALOG_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 3. Close the writer-without-reader gap: any role holding a per-catalog write
--    key can now read the catalogs too.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name LIKE 'CATALOG\_%\_WRITE'
         CROSS JOIN permissions np
WHERE np.name = 'CATALOG_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 4. Fail loudly rather than migrate into a half-applied state.
DO $$
DECLARE
    n int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'CATALOG_VIEW_ALL') THEN
        RAISE EXCEPTION 'V34: CATALOG_VIEW_ALL was not created';
    END IF;

    -- SYSTEM must hold it (V30 trigger).
    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'CATALOG_VIEW_ALL'
    ) THEN
        RAISE EXCEPTION 'V34: SYSTEM did not receive CATALOG_VIEW_ALL (V30 trigger?)';
    END IF;

    -- Behavior-preserving invariant: every USER_VIEW_ALL holder now also holds
    -- CATALOG_VIEW_ALL, so no role loses catalog read on deploy.
    SELECT count(*) INTO n
    FROM role_permissions rv
             JOIN permissions pv ON pv.permissions_id = rv.permission_id AND pv.name = 'USER_VIEW_ALL'
    WHERE NOT EXISTS (
        SELECT 1
        FROM role_permissions rc
                 JOIN permissions pc ON pc.permissions_id = rc.permission_id AND pc.name = 'CATALOG_VIEW_ALL'
        WHERE rc.role_id = rv.role_id
    );
    IF n > 0 THEN
        RAISE EXCEPTION 'V34: % role(s) hold USER_VIEW_ALL but not CATALOG_VIEW_ALL', n;
    END IF;

    -- Writer-without-reader gap closed: every catalog-writer role also reads.
    SELECT count(DISTINCT rw.role_id) INTO n
    FROM role_permissions rw
             JOIN permissions pw ON pw.permissions_id = rw.permission_id AND pw.name LIKE 'CATALOG\_%\_WRITE'
    WHERE NOT EXISTS (
        SELECT 1
        FROM role_permissions rc
                 JOIN permissions pc ON pc.permissions_id = rc.permission_id AND pc.name = 'CATALOG_VIEW_ALL'
        WHERE rc.role_id = rw.role_id
    );
    IF n > 0 THEN
        RAISE EXCEPTION 'V34: % catalog-writer role(s) lack CATALOG_VIEW_ALL', n;
    END IF;
END $$;
