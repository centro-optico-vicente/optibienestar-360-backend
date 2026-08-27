SET search_path TO app, public;

-- V78: split each catalog's combined CATALOG_<ENTITY>_WRITE (V33/V43) and the
-- shared CATALOG_VIEW_ALL (V34) into 4 granular permissions per entity:
-- <ENTITY>_VIEW_ALL / _CREATE / _UPDATE / _DELETE. Same split for subsidies'
-- combined SUBSIDY_APPROVE (V37-ish) into SUBSIDY_CREATE/_UPDATE/_DELETE.
--
-- Today a role either has full CRUD on a catalog or none — a "coordinador de
-- ventas" role cannot have full CRUD on promoter types but view-only on ally
-- types. Naming follows the entity-first convention already used by the audit
-- permissions (COUNTRY_RECORD_AUDIT_VIEW, not CATALOG_COUNTRY_RECORD_AUDIT_VIEW).
-- Domains are untouched — V76 already gave each catalog its own domain
-- (CATALOG_COUNTRY, ..., CATALOG_PROMOTER_TYPE); only permission names change.


-- 1. The 44 new catalog permissions, 4 per entity in its existing domain.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COUNTRY_VIEW_ALL',           'CATALOG_COUNTRY',          'Ver países'),
    ('COUNTRY_CREATE',             'CATALOG_COUNTRY',          'Crear países'),
    ('COUNTRY_UPDATE',             'CATALOG_COUNTRY',          'Editar países'),
    ('COUNTRY_DELETE',             'CATALOG_COUNTRY',          'Eliminar países'),

    ('STATE_VIEW_ALL',             'CATALOG_STATE',            'Ver estados / departamentos'),
    ('STATE_CREATE',               'CATALOG_STATE',            'Crear estados / departamentos'),
    ('STATE_UPDATE',               'CATALOG_STATE',            'Editar estados / departamentos'),
    ('STATE_DELETE',               'CATALOG_STATE',            'Eliminar estados / departamentos'),

    ('CITY_VIEW_ALL',              'CATALOG_CITY',              'Ver ciudades'),
    ('CITY_CREATE',                'CATALOG_CITY',              'Crear ciudades'),
    ('CITY_UPDATE',                'CATALOG_CITY',              'Editar ciudades'),
    ('CITY_DELETE',                'CATALOG_CITY',              'Eliminar ciudades'),

    ('GENDER_VIEW_ALL',            'CATALOG_GENDER',            'Ver géneros'),
    ('GENDER_CREATE',              'CATALOG_GENDER',            'Crear géneros'),
    ('GENDER_UPDATE',              'CATALOG_GENDER',            'Editar géneros'),
    ('GENDER_DELETE',              'CATALOG_GENDER',            'Eliminar géneros'),

    ('DOCUMENT_TYPE_VIEW_ALL',     'CATALOG_DOCUMENT_TYPE',     'Ver tipos de documento'),
    ('DOCUMENT_TYPE_CREATE',       'CATALOG_DOCUMENT_TYPE',     'Crear tipos de documento'),
    ('DOCUMENT_TYPE_UPDATE',       'CATALOG_DOCUMENT_TYPE',     'Editar tipos de documento'),
    ('DOCUMENT_TYPE_DELETE',       'CATALOG_DOCUMENT_TYPE',     'Eliminar tipos de documento'),

    ('MARITAL_STATUS_VIEW_ALL',    'CATALOG_MARITAL_STATUS',    'Ver estados civiles'),
    ('MARITAL_STATUS_CREATE',      'CATALOG_MARITAL_STATUS',    'Crear estados civiles'),
    ('MARITAL_STATUS_UPDATE',      'CATALOG_MARITAL_STATUS',    'Editar estados civiles'),
    ('MARITAL_STATUS_DELETE',      'CATALOG_MARITAL_STATUS',    'Eliminar estados civiles'),

    ('OCCUPATION_VIEW_ALL',        'CATALOG_OCCUPATION',        'Ver ocupaciones'),
    ('OCCUPATION_CREATE',          'CATALOG_OCCUPATION',        'Crear ocupaciones'),
    ('OCCUPATION_UPDATE',          'CATALOG_OCCUPATION',        'Editar ocupaciones'),
    ('OCCUPATION_DELETE',          'CATALOG_OCCUPATION',        'Eliminar ocupaciones'),

    ('MEDICAL_SPECIALTY_VIEW_ALL', 'CATALOG_MEDICAL_SPECIALTY', 'Ver especialidades médicas'),
    ('MEDICAL_SPECIALTY_CREATE',   'CATALOG_MEDICAL_SPECIALTY', 'Crear especialidades médicas'),
    ('MEDICAL_SPECIALTY_UPDATE',   'CATALOG_MEDICAL_SPECIALTY', 'Editar especialidades médicas'),
    ('MEDICAL_SPECIALTY_DELETE',   'CATALOG_MEDICAL_SPECIALTY', 'Eliminar especialidades médicas'),

    ('SERVICE_CATEGORY_VIEW_ALL',  'CATALOG_SERVICE_CATEGORY',  'Ver categorías de servicio'),
    ('SERVICE_CATEGORY_CREATE',    'CATALOG_SERVICE_CATEGORY',  'Crear categorías de servicio'),
    ('SERVICE_CATEGORY_UPDATE',    'CATALOG_SERVICE_CATEGORY',  'Editar categorías de servicio'),
    ('SERVICE_CATEGORY_DELETE',    'CATALOG_SERVICE_CATEGORY',  'Eliminar categorías de servicio'),

    ('ALLY_TYPE_VIEW_ALL',         'CATALOG_ALLY_TYPE',         'Ver tipos de aliado'),
    ('ALLY_TYPE_CREATE',           'CATALOG_ALLY_TYPE',         'Crear tipos de aliado'),
    ('ALLY_TYPE_UPDATE',           'CATALOG_ALLY_TYPE',         'Editar tipos de aliado'),
    ('ALLY_TYPE_DELETE',           'CATALOG_ALLY_TYPE',         'Eliminar tipos de aliado'),

    ('PROMOTER_TYPE_VIEW_ALL',     'CATALOG_PROMOTER_TYPE',     'Ver tipos de promotor'),
    ('PROMOTER_TYPE_CREATE',       'CATALOG_PROMOTER_TYPE',     'Crear tipos de promotor'),
    ('PROMOTER_TYPE_UPDATE',       'CATALOG_PROMOTER_TYPE',     'Editar tipos de promotor'),
    ('PROMOTER_TYPE_DELETE',       'CATALOG_PROMOTER_TYPE',     'Eliminar tipos de promotor')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- 2. The 3 new subsidy permissions, same SUBSIDIES domain as SUBSIDY_VIEW_ALL/_VIEW_OWN.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('SUBSIDY_CREATE', 'Aplicar un descuento puntual a un pago pendiente'),
    ('SUBSIDY_UPDATE', 'Modificar un subsidio o exoneración'),
    ('SUBSIDY_DELETE', 'Revocar un subsidio o exoneración')
) AS v(name, description)
JOIN permission_domains pd ON pd.code = 'SUBSIDIES';


-- 3. Backfill: every role with the shared CATALOG_VIEW_ALL gets all 11 new
--    <ENTITY>_VIEW_ALL permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'CATALOG_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name LIKE '%\_VIEW\_ALL'
  AND np.name IN (
      'COUNTRY_VIEW_ALL', 'STATE_VIEW_ALL', 'CITY_VIEW_ALL', 'GENDER_VIEW_ALL',
      'DOCUMENT_TYPE_VIEW_ALL', 'MARITAL_STATUS_VIEW_ALL', 'OCCUPATION_VIEW_ALL',
      'MEDICAL_SPECIALTY_VIEW_ALL', 'SERVICE_CATEGORY_VIEW_ALL', 'ALLY_TYPE_VIEW_ALL',
      'PROMOTER_TYPE_VIEW_ALL'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 4. Backfill: every role with a per-catalog CATALOG_<ENTITY>_WRITE gets that
--    same entity's new CREATE + UPDATE + DELETE.
WITH entity_map(old_write, new_prefix) AS (
    VALUES
        ('CATALOG_COUNTRY_WRITE',           'COUNTRY'),
        ('CATALOG_STATE_WRITE',             'STATE'),
        ('CATALOG_CITY_WRITE',              'CITY'),
        ('CATALOG_GENDER_WRITE',            'GENDER'),
        ('CATALOG_DOCUMENT_TYPE_WRITE',     'DOCUMENT_TYPE'),
        ('CATALOG_MARITAL_STATUS_WRITE',    'MARITAL_STATUS'),
        ('CATALOG_OCCUPATION_WRITE',        'OCCUPATION'),
        ('CATALOG_MEDICAL_SPECIALTY_WRITE', 'MEDICAL_SPECIALTY'),
        ('CATALOG_SERVICE_CATEGORY_WRITE',  'SERVICE_CATEGORY'),
        ('CATALOG_ALLY_TYPE_WRITE',         'ALLY_TYPE'),
        ('CATALOG_PROMOTER_TYPE_WRITE',     'PROMOTER_TYPE')
),
     suffixes(suffix) AS (VALUES ('CREATE'), ('UPDATE'), ('DELETE'))
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
         JOIN entity_map em ON em.old_write = p.name
         CROSS JOIN suffixes s
         JOIN permissions np ON np.name = em.new_prefix || '_' || s.suffix
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 5. Backfill: every role with SUBSIDY_APPROVE gets SUBSIDY_CREATE/_UPDATE/_DELETE.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'SUBSIDY_APPROVE'
         CROSS JOIN permissions np
WHERE np.name IN ('SUBSIDY_CREATE', 'SUBSIDY_UPDATE', 'SUBSIDY_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- 6. Drop the superseded keys. The V30 ON DELETE CASCADE on
--    role_permissions.permission_id clears their grants automatically.
DELETE FROM permissions WHERE name IN (
    'CATALOG_VIEW_ALL',
    'CATALOG_COUNTRY_WRITE', 'CATALOG_STATE_WRITE', 'CATALOG_CITY_WRITE',
    'CATALOG_GENDER_WRITE', 'CATALOG_DOCUMENT_TYPE_WRITE', 'CATALOG_MARITAL_STATUS_WRITE',
    'CATALOG_OCCUPATION_WRITE', 'CATALOG_MEDICAL_SPECIALTY_WRITE', 'CATALOG_SERVICE_CATEGORY_WRITE',
    'CATALOG_ALLY_TYPE_WRITE', 'CATALOG_PROMOTER_TYPE_WRITE',
    'SUBSIDY_APPROVE'
);

-- 7. The CATALOGS domain only ever held CATALOG_VIEW_ALL (V76) — now empty, drop it.
DELETE FROM permission_domains WHERE code = 'CATALOGS';


-- 8. Fail loudly rather than migrate into a half-applied state.
DO $$
DECLARE
    n int;
    problem text;
BEGIN
    SELECT count(*) INTO n FROM permissions
    WHERE name IN (
        'COUNTRY_VIEW_ALL', 'COUNTRY_CREATE', 'COUNTRY_UPDATE', 'COUNTRY_DELETE',
        'STATE_VIEW_ALL', 'STATE_CREATE', 'STATE_UPDATE', 'STATE_DELETE',
        'CITY_VIEW_ALL', 'CITY_CREATE', 'CITY_UPDATE', 'CITY_DELETE',
        'GENDER_VIEW_ALL', 'GENDER_CREATE', 'GENDER_UPDATE', 'GENDER_DELETE',
        'DOCUMENT_TYPE_VIEW_ALL', 'DOCUMENT_TYPE_CREATE', 'DOCUMENT_TYPE_UPDATE', 'DOCUMENT_TYPE_DELETE',
        'MARITAL_STATUS_VIEW_ALL', 'MARITAL_STATUS_CREATE', 'MARITAL_STATUS_UPDATE', 'MARITAL_STATUS_DELETE',
        'OCCUPATION_VIEW_ALL', 'OCCUPATION_CREATE', 'OCCUPATION_UPDATE', 'OCCUPATION_DELETE',
        'MEDICAL_SPECIALTY_VIEW_ALL', 'MEDICAL_SPECIALTY_CREATE', 'MEDICAL_SPECIALTY_UPDATE', 'MEDICAL_SPECIALTY_DELETE',
        'SERVICE_CATEGORY_VIEW_ALL', 'SERVICE_CATEGORY_CREATE', 'SERVICE_CATEGORY_UPDATE', 'SERVICE_CATEGORY_DELETE',
        'ALLY_TYPE_VIEW_ALL', 'ALLY_TYPE_CREATE', 'ALLY_TYPE_UPDATE', 'ALLY_TYPE_DELETE',
        'PROMOTER_TYPE_VIEW_ALL', 'PROMOTER_TYPE_CREATE', 'PROMOTER_TYPE_UPDATE', 'PROMOTER_TYPE_DELETE',
        'SUBSIDY_CREATE', 'SUBSIDY_UPDATE', 'SUBSIDY_DELETE'
    );
    IF n <> 47 THEN
        RAISE EXCEPTION 'V78: expected 47 new permissions, found %', n;
    END IF;

    SELECT count(*) INTO n FROM permissions
    WHERE name IN (
        'CATALOG_VIEW_ALL', 'CATALOG_COUNTRY_WRITE', 'CATALOG_STATE_WRITE', 'CATALOG_CITY_WRITE',
        'CATALOG_GENDER_WRITE', 'CATALOG_DOCUMENT_TYPE_WRITE', 'CATALOG_MARITAL_STATUS_WRITE',
        'CATALOG_OCCUPATION_WRITE', 'CATALOG_MEDICAL_SPECIALTY_WRITE', 'CATALOG_SERVICE_CATEGORY_WRITE',
        'CATALOG_ALLY_TYPE_WRITE', 'CATALOG_PROMOTER_TYPE_WRITE', 'SUBSIDY_APPROVE'
    );
    IF n <> 0 THEN
        RAISE EXCEPTION 'V78: % superseded permission(s) still exist', n;
    END IF;

    IF EXISTS (SELECT 1 FROM permission_domains WHERE code = 'CATALOGS') THEN
        RAISE EXCEPTION 'V78: CATALOGS domain still exists — should have been dropped';
    END IF;

    -- SYSTEM must hold every new permission (V30 trigger).
    SELECT count(*) INTO n
    FROM permissions p
    WHERE p.name IN (
        'COUNTRY_VIEW_ALL', 'SUBSIDY_CREATE', 'PROMOTER_TYPE_DELETE'
    )
      AND NOT EXISTS (
          SELECT 1 FROM role_permissions rp
                            JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
          WHERE rp.permission_id = p.permissions_id
      );
    IF n > 0 THEN
        RAISE EXCEPTION 'V78: SYSTEM is missing % of the sampled new permissions', n;
    END IF;
END $$;
