SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V76: re-group permissions by entity instead of by module, for the admin
-- roles/permissions UI (spec 07-audit.md). Two changes, both purely
-- reclassifying `permissions.domain_id` — `permission_domains` is only used
-- to group the catalog for display (GET /v1/admin/permissions); every
-- @PreAuthorize check reads the permission `name` directly and is unaffected.
--
-- 1) ROLES: V32 nested ROLE_* permissions inside the USERS domain ("one
--    governance domain"). Split them into their own ROLES domain so
--    "Usuarios" only lists user-management permissions.
--
-- 2) CATALOGS: V33/V34/V43/V73 parked 11 unrelated small lookup entities
--    (country, state, city, gender, document_type, marital_status,
--    occupation, medical_specialty, service_category, ally_type,
--    promoter_type) under one catch-all domain. Split each entity's WRITE +
--    AUDIT_VIEW + REPORT_AUDIT_VIEW permissions into its own domain.
--    CATALOG_VIEW_ALL is a single permission shared by all catalog read
--    endpoints (AdminCatalogsController.READ_AUTH) — it has no single entity
--    home, so it stays in CATALOGS, which becomes a small "general" domain.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES
    ('ROLES',                    'Roles',                     'i-lucide-shield',           'Gestión de roles del sistema',                        15),
    ('CATALOG_COUNTRY',          'Países',                     'i-lucide-flag',              'Catálogo de países',                                   111),
    ('CATALOG_STATE',            'Estados / Departamentos',   'i-lucide-map',               'Catálogo de estados/departamentos',                    112),
    ('CATALOG_CITY',             'Ciudades',                   'i-lucide-building-2',        'Catálogo de ciudades',                                 113),
    ('CATALOG_GENDER',           'Géneros',                    'i-lucide-venus-and-mars',    'Catálogo de géneros',                                  114),
    ('CATALOG_DOCUMENT_TYPE',    'Tipos de documento',        'i-lucide-id-card',           'Catálogo de tipos de documento',                       115),
    ('CATALOG_MARITAL_STATUS',   'Estados civiles',           'i-lucide-heart',             'Catálogo de estados civiles',                          116),
    ('CATALOG_OCCUPATION',       'Ocupaciones',                'i-lucide-briefcase',         'Catálogo de ocupaciones',                              117),
    ('CATALOG_MEDICAL_SPECIALTY', 'Especialidades médicas',   'i-lucide-stethoscope',       'Catálogo de especialidades médicas',                   118),
    ('CATALOG_SERVICE_CATEGORY', 'Categorías de servicio',    'i-lucide-layout-grid',       'Catálogo de categorías de servicio',                   119),
    ('CATALOG_ALLY_TYPE',        'Tipos de aliado',           'i-lucide-handshake',         'Catálogo de tipos de aliado',                          120),
    ('CATALOG_PROMOTER_TYPE',    'Tipos de promotor',         'i-lucide-megaphone',         'Catálogo de tipos de promotor',                        121);


-- ROLES: move every ROLE_* permission (V6, V32, V74, V75) out of USERS.
UPDATE permissions
SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'ROLES')
WHERE name IN (
    'ROLE_VIEW', 'ROLE_CREATE', 'ROLE_UPDATE', 'ROLE_DELETE',
    'ROLE_PERMISSION_EDIT', 'ROLE_USERS_MANAGE',
    'ROLE_AUDIT_VIEW', 'ROLE_AUDIT_RESTORE', 'ROLE_REPORT_AUDIT_VIEW'
);

-- CATALOGS split: one UPDATE per entity, moving its WRITE + audit pair (when
-- it has one — ALLY_TYPE never got an audit pair in V73).
UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_COUNTRY')
WHERE name IN ('CATALOG_COUNTRY_WRITE', 'COUNTRY_AUDIT_VIEW', 'COUNTRY_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_STATE')
WHERE name IN ('CATALOG_STATE_WRITE', 'STATE_AUDIT_VIEW', 'STATE_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_CITY')
WHERE name IN ('CATALOG_CITY_WRITE', 'CITY_AUDIT_VIEW', 'CITY_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_GENDER')
WHERE name IN ('CATALOG_GENDER_WRITE', 'GENDER_AUDIT_VIEW', 'GENDER_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_DOCUMENT_TYPE')
WHERE name IN ('CATALOG_DOCUMENT_TYPE_WRITE', 'DOCUMENT_TYPE_AUDIT_VIEW', 'DOCUMENT_TYPE_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_MARITAL_STATUS')
WHERE name IN ('CATALOG_MARITAL_STATUS_WRITE', 'MARITAL_STATUS_AUDIT_VIEW', 'MARITAL_STATUS_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_OCCUPATION')
WHERE name IN ('CATALOG_OCCUPATION_WRITE', 'OCCUPATION_AUDIT_VIEW', 'OCCUPATION_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_MEDICAL_SPECIALTY')
WHERE name IN ('CATALOG_MEDICAL_SPECIALTY_WRITE', 'MEDICAL_SPECIALTY_AUDIT_VIEW', 'MEDICAL_SPECIALTY_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_SERVICE_CATEGORY')
WHERE name IN ('CATALOG_SERVICE_CATEGORY_WRITE', 'SERVICE_CATEGORY_AUDIT_VIEW', 'SERVICE_CATEGORY_REPORT_AUDIT_VIEW');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_ALLY_TYPE')
WHERE name IN ('CATALOG_ALLY_TYPE_WRITE');

UPDATE permissions SET domain_id = (SELECT permission_domains_id FROM permission_domains WHERE code = 'CATALOG_PROMOTER_TYPE')
WHERE name IN ('CATALOG_PROMOTER_TYPE_WRITE', 'PROMOTER_TYPE_AUDIT_VIEW', 'PROMOTER_TYPE_REPORT_AUDIT_VIEW');


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_domain TEXT;
    stray_count INT;
BEGIN
    FOREACH new_domain IN ARRAY ARRAY[
        'ROLES', 'CATALOG_COUNTRY', 'CATALOG_STATE', 'CATALOG_CITY', 'CATALOG_GENDER',
        'CATALOG_DOCUMENT_TYPE', 'CATALOG_MARITAL_STATUS', 'CATALOG_OCCUPATION',
        'CATALOG_MEDICAL_SPECIALTY', 'CATALOG_SERVICE_CATEGORY', 'CATALOG_ALLY_TYPE',
        'CATALOG_PROMOTER_TYPE'
    ]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permission_domains WHERE code = new_domain) THEN
            RAISE EXCEPTION 'V76: domain % was not created', new_domain;
        END IF;
    END LOOP;

    -- Every ROLE_* permission must now point at ROLES, none left in USERS.
    SELECT COUNT(*) INTO stray_count
    FROM permissions p
             JOIN permission_domains pd ON pd.permission_domains_id = p.domain_id
    WHERE p.name LIKE 'ROLE\_%' AND pd.code <> 'ROLES';
    IF stray_count > 0 THEN
        RAISE EXCEPTION 'V76: % ROLE_* permissions were not moved to ROLES', stray_count;
    END IF;

    -- CATALOGS should only retain CATALOG_VIEW_ALL after the split.
    SELECT COUNT(*) INTO stray_count
    FROM permissions p
             JOIN permission_domains pd ON pd.permission_domains_id = p.domain_id
    WHERE pd.code = 'CATALOGS' AND p.name <> 'CATALOG_VIEW_ALL';
    IF stray_count > 0 THEN
        RAISE EXCEPTION 'V76: % permissions unexpectedly remain in CATALOGS', stray_count;
    END IF;
END $$;
