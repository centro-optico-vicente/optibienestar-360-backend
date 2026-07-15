SET search_path TO app, public;

-- V33: one write permission per catalog, replacing the all-or-nothing CATALOG_WRITE.
--
-- CATALOG_WRITE (V32) opened every catalog at once, so access could not be
-- delegated: granting occupations to an HR-style role also handed over countries.
-- Each catalog now has its own key.
--
-- Reads stay on USER_VIEW_ALL by design — forms across the app populate their
-- dropdowns from these same endpoints, so a per-catalog read key would break
-- unrelated screens.


-- 1. One write key per catalog, in the CATALOGS domain created by V32.
--    The V30 trigger auto-grants each of these to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('CATALOG_COUNTRY_WRITE',            'Crear, editar y eliminar países'),
    ('CATALOG_STATE_WRITE',              'Crear, editar y eliminar estados / departamentos'),
    ('CATALOG_CITY_WRITE',               'Crear, editar y eliminar ciudades'),
    ('CATALOG_GENDER_WRITE',             'Crear, editar y eliminar géneros'),
    ('CATALOG_DOCUMENT_TYPE_WRITE',      'Crear, editar y eliminar tipos de documento'),
    ('CATALOG_MARITAL_STATUS_WRITE',     'Crear, editar y eliminar estados civiles'),
    ('CATALOG_OCCUPATION_WRITE',         'Crear, editar y eliminar ocupaciones'),
    ('CATALOG_MEDICAL_SPECIALTY_WRITE',  'Crear, editar y eliminar especialidades médicas'),
    ('CATALOG_SERVICE_CATEGORY_WRITE',   'Crear, editar y eliminar categorías de servicio'),
    ('CATALOG_ALLY_TYPE_WRITE',          'Crear, editar y eliminar tipos de aliado')
) AS v(name, description)
CROSS JOIN permission_domains pd
WHERE pd.code = 'CATALOGS';


-- 2. Anyone who could already write catalogs keeps every catalog. Runs before the
--    CATALOG_WRITE row is dropped, while the old grants still exist.
INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
         JOIN permissions np ON np.name LIKE 'CATALOG\_%\_WRITE'
WHERE p.name = 'CATALOG_WRITE'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 3. ADMINISTRADOR gets every catalog. It already reached the Datos maestros
--    screens (the nav gated them by role name) but every save answered 403,
--    since CATALOG_WRITE was SYSTEM-only. Granting the full set makes the screens
--    it can already see actually work; narrower roles are built by granting a
--    single CATALOG_*_WRITE.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name LIKE 'CATALOG\_%\_WRITE'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 4. Drop the superseded key. The V30 ON DELETE CASCADE on
--    role_permissions.permission_id clears its grants automatically.
DELETE FROM permissions WHERE name = 'CATALOG_WRITE';


-- 5. Fail loudly rather than migrate into a half-applied state.
DO $$
DECLARE
    problem text;
BEGIN
    IF EXISTS (SELECT 1 FROM permissions WHERE name = 'CATALOG_WRITE') THEN
        RAISE EXCEPTION 'V33: CATALOG_WRITE still exists — it should have been replaced';
    END IF;

    SELECT count(*)::text INTO problem FROM permissions WHERE name LIKE 'CATALOG\_%\_WRITE';
    IF problem <> '10' THEN
        RAISE EXCEPTION 'V33: expected 10 per-catalog write permissions, found %', problem;
    END IF;

    SELECT string_agg(r.name, ', ') INTO problem
    FROM roles r
    WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
      AND (SELECT count(*)
           FROM role_permissions rp
                    JOIN permissions p ON p.permissions_id = rp.permission_id
           WHERE rp.role_id = r.roles_id
             AND p.name LIKE 'CATALOG\_%\_WRITE') <> 10;
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V33: these roles did not end up with all 10 catalog write permissions: %', problem;
    END IF;
END $$;
