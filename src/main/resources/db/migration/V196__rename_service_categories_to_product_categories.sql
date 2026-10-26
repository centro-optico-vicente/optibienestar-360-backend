SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V196: service_categories → product_categories (hub ADR 0026).
--
-- The catalog classifies both services and products now (the `products`
-- master in V197 carries the SERVICE/PRODUCT flag), and the ally network is
-- no longer health-only — so the "service" name is renamed end to end:
-- table, PK, the FK column on benefit_usages, index/trigger names,
-- permissions, permission domain and audit keys.
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. Table, PK and objects ───────────────────────────────────────────────
ALTER TABLE service_categories RENAME TO product_categories;
ALTER TABLE product_categories RENAME COLUMN service_categories_id TO product_categories_id;

ALTER INDEX idx_service_categories_is_active RENAME TO idx_product_categories_is_active;
ALTER TRIGGER trg_service_categories_updated_at ON product_categories
    RENAME TO trg_product_categories_updated_at;

-- Identity sequence and auto-named constraints (pkey/uuid_key/code_key/name_key;
-- renaming a pkey/unique constraint renames its index too).
DO $$
DECLARE
    c RECORD;
BEGIN
    EXECUTE format('ALTER SEQUENCE %s RENAME TO product_categories_product_categories_id_seq',
                   pg_get_serial_sequence('app.product_categories', 'product_categories_id'));
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'app.product_categories'::regclass AND conname LIKE '%service_categories%'
    LOOP
        EXECUTE format('ALTER TABLE product_categories RENAME CONSTRAINT %I TO %I',
                       c.conname, replace(c.conname, 'service_categories', 'product_categories'));
    END LOOP;
    FOR c IN SELECT conname, conrelid::regclass AS rel FROM pg_constraint
             WHERE contype = 'f' AND conname LIKE '%service_category%'
               AND conrelid = 'app.benefit_usages'::regclass
    LOOP
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
                       c.rel, c.conname, replace(c.conname, 'service_category', 'product_category'));
    END LOOP;
END $$;

COMMENT ON TABLE product_categories IS
    'Clasificación de productos y servicios ofrecidos por aliados (antes service_categories, ADR 0026).';

-- ─── 2. benefit_usages FK column ────────────────────────────────────────────
ALTER TABLE benefit_usages RENAME COLUMN service_category_id TO product_category_id;

COMMENT ON COLUMN benefit_usages.product_category_id IS
    'Rubro del consumo. Se copia del producto de la oferta cuando la hay; si no, lo elige el operador. Obligatorio para que todo consumo entre en el reporte por rubro.';

-- ─── 3. Permissions and their domain ────────────────────────────────────────
-- Covers SERVICE_CATEGORY_{VIEW_ALL,CREATE,UPDATE,DELETE},
-- SERVICE_CATEGORY_{RECORD,REPORT}_AUDIT_VIEW and CATALOG_SERVICE_CATEGORY_WRITE.
-- role_permissions references permissions by id, so grants survive the rename.
UPDATE permissions
SET name        = replace(name, 'SERVICE_CATEGORY', 'PRODUCT_CATEGORY'),
    description = replace(replace(description, 'categorías de servicio', 'categorías de producto'),
                          'categoría de servicio', 'categoría de producto')
WHERE name LIKE '%SERVICE_CATEGORY%';

UPDATE permission_domains
SET code        = 'CATALOG_PRODUCT_CATEGORY',
    name        = 'Categorías de producto',
    description = 'Catálogo de categorías de productos y servicios'
WHERE code = 'CATALOG_SERVICE_CATEGORY';

-- ─── 4. Audit keys ──────────────────────────────────────────────────────────
UPDATE entity_config
SET entity_key   = 'product_category',
    display_name = 'Categorías de producto',
    table_name   = 'product_categories'
WHERE entity_key = 'service_category';

UPDATE data_change_audit_log SET entity_key = 'product_category' WHERE entity_key = 'service_category';
UPDATE report_audit_log      SET entity_key = 'product_category' WHERE entity_key = 'service_category';

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM permissions WHERE name LIKE '%SERVICE_CATEGORY%') THEN
        RAISE EXCEPTION 'V196: SERVICE_CATEGORY permissions left behind';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'PRODUCT_CATEGORY_VIEW_ALL') THEN
        RAISE EXCEPTION 'V196: PRODUCT_CATEGORY_VIEW_ALL missing after rename';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM permission_domains WHERE code = 'CATALOG_PRODUCT_CATEGORY') THEN
        RAISE EXCEPTION 'V196: CATALOG_PRODUCT_CATEGORY domain missing after rename';
    END IF;
END $$;
