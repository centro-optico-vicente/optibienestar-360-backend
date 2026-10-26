SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V198: permissions, audit keys and attached files for the products model
-- (hub ADR 0026, after V197).
--
-- Permissions stay granular (one per action, no *_MANAGE):
--   PRODUCTS domain (new)  — the master catalog:
--     PRODUCT_{VIEW_ALL,CREATE,UPDATE,DELETE}, PRODUCT_IMAGE_{UPLOAD,DELETE}
--     (renamed from ALLY_SERVICE_IMAGE_*: the image now lives on the product),
--     PRODUCT_{RECORD,REPORT}_AUDIT_VIEW.
--   ALLIES domain          — an ally's offers:
--     ALLY_PRODUCT_{VIEW_ALL,CREATE,UPDATE,DELETE},
--     ALLY_PRODUCT_APPROVE (renamed from ALLY_SERVICE_APPROVE).
--
-- New permissions are granted to the roles that already held the equivalent
-- authority over ally services (ALLY_VIEW_ALL / ALLY_UPDATE / ALLY_*_AUDIT_VIEW),
-- so no role gains or loses reach. SYSTEM receives them through the V30 trigger.
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. PRODUCTS permission domain ──────────────────────────────────────────
INSERT INTO permission_domains (code, name, icon, description, display_order)
SELECT 'PRODUCTS', 'Productos y servicios', 'i-lucide-package',
       'Catálogo maestro de productos y servicios ofrecidos por aliados', display_order + 1
FROM permission_domains
WHERE code = 'ALLIES';

-- ─── 2. Renamed permissions ─────────────────────────────────────────────────
UPDATE permissions
SET name        = 'ALLY_PRODUCT_APPROVE',
    description = 'Aprobar o rechazar productos y servicios propuestos por aliados'
WHERE name = 'ALLY_SERVICE_APPROVE';

UPDATE permissions
SET name        = replace(name, 'ALLY_SERVICE_IMAGE_', 'PRODUCT_IMAGE_'),
    description = replace(description, 'un servicio de catálogo', 'un producto del catálogo'),
    domain_id   = (SELECT permission_domains_id FROM permission_domains WHERE code = 'PRODUCTS')
WHERE name IN ('ALLY_SERVICE_IMAGE_UPLOAD', 'ALLY_SERVICE_IMAGE_DELETE');

-- ─── 3. New permissions ─────────────────────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('PRODUCT_VIEW_ALL',            'PRODUCTS', 'Ver el catálogo maestro de productos y servicios'),
    ('PRODUCT_CREATE',              'PRODUCTS', 'Crear productos y servicios en el catálogo maestro'),
    ('PRODUCT_UPDATE',              'PRODUCTS', 'Editar productos y servicios del catálogo maestro'),
    ('PRODUCT_DELETE',              'PRODUCTS', 'Eliminar productos y servicios del catálogo maestro'),
    ('PRODUCT_RECORD_AUDIT_VIEW',   'PRODUCTS', 'Ver el historial de cambios de un producto'),
    ('PRODUCT_REPORT_AUDIT_VIEW',   'PRODUCTS', 'Ver el historial de reportes generados de productos'),
    ('ALLY_PRODUCT_VIEW_ALL',       'ALLIES',   'Ver los productos y servicios que ofrece cada aliado'),
    ('ALLY_PRODUCT_CREATE',         'ALLIES',   'Agregar productos y servicios a la oferta de un aliado'),
    ('ALLY_PRODUCT_UPDATE',         'ALLIES',   'Editar precio, descuento y exclusiones de la oferta de un aliado'),
    ('ALLY_PRODUCT_DELETE',         'ALLIES',   'Quitar productos y servicios de la oferta de un aliado')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- ─── 4. Grants mirror the authority each role already had ───────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM (VALUES
    ('ALLY_VIEW_ALL',          'PRODUCT_VIEW_ALL'),
    ('ALLY_VIEW_ALL',          'ALLY_PRODUCT_VIEW_ALL'),
    ('ALLY_UPDATE',            'PRODUCT_CREATE'),
    ('ALLY_UPDATE',            'PRODUCT_UPDATE'),
    ('ALLY_UPDATE',            'PRODUCT_DELETE'),
    ('ALLY_UPDATE',            'ALLY_PRODUCT_CREATE'),
    ('ALLY_UPDATE',            'ALLY_PRODUCT_UPDATE'),
    ('ALLY_UPDATE',            'ALLY_PRODUCT_DELETE'),
    ('ALLY_RECORD_AUDIT_VIEW', 'PRODUCT_RECORD_AUDIT_VIEW'),
    ('ALLY_REPORT_AUDIT_VIEW', 'PRODUCT_REPORT_AUDIT_VIEW')
) AS m(existing, granted)
JOIN permissions ep ON ep.name = m.existing
JOIN role_permissions rp ON rp.permission_id = ep.permissions_id
JOIN permissions np ON np.name = m.granted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── 5. Audit keys ──────────────────────────────────────────────────────────
-- Offers keep the ally audit permissions (AuditEntityAccess maps
-- ally_product → ALLY_*_AUDIT_VIEW, as ally_service did).
UPDATE entity_config
SET entity_key   = 'ally_product',
    display_name = 'Productos y servicios de aliado',
    table_name   = 'ally_products'
WHERE entity_key = 'ally_service';

INSERT INTO entity_config (entity_key, display_name, table_name)
VALUES ('product', 'Productos y servicios', 'products')
ON CONFLICT (entity_key) DO NOTHING;

UPDATE data_change_audit_log SET entity_key = 'ally_product' WHERE entity_key = 'ally_service';
UPDATE report_audit_log      SET entity_key = 'ally_product' WHERE entity_key = 'ally_service';

-- ─── 6. Catalog images now belong to the product ────────────────────────────
-- owner_uuid moves from the offer (old ally_services.uuid, kept by
-- ally_products) to its backfilled exclusive product. file_key is NOT
-- rewritten here: the R2 objects still sit under the legacy prefix until
-- LegacyStorageKeyMigrationRunner copies them and updates file_key/image_key.
UPDATE attached_files af
SET owner_table = 'products',
    owner_uuid  = p.uuid
FROM ally_products ap
         JOIN products p ON p.products_id = ap.product_id
WHERE af.owner_table = 'ally_services'
  AND af.owner_uuid = ap.uuid;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM permissions WHERE name LIKE 'ALLY_SERVICE%') THEN
        RAISE EXCEPTION 'V198: ALLY_SERVICE permissions left behind';
    END IF;
    IF EXISTS (SELECT 1 FROM attached_files WHERE owner_table = 'ally_services') THEN
        RAISE EXCEPTION 'V198: attached_files still owned by ally_services';
    END IF;
    FOREACH new_permission IN ARRAY ARRAY['PRODUCT_VIEW_ALL', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'PRODUCT_DELETE',
        'PRODUCT_IMAGE_UPLOAD', 'PRODUCT_IMAGE_DELETE', 'ALLY_PRODUCT_APPROVE',
        'ALLY_PRODUCT_VIEW_ALL', 'ALLY_PRODUCT_CREATE', 'ALLY_PRODUCT_UPDATE', 'ALLY_PRODUCT_DELETE']
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r      ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V198: SYSTEM does not hold %', new_permission;
        END IF;
    END LOOP;
END $$;
