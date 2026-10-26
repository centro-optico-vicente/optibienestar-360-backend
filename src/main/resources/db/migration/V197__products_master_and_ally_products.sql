SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V197: products master + ally_products offers (hub ADR 0026).
--
-- Until now each ally owned its catalog rows in ally_services (name,
-- category, price, discount, image, review workflow all in one row), so the
-- same item was re-typed per ally and the catalog could only hold services.
--
-- New model:
--   products       — catalog master. product_type SERVICE | PRODUCT.
--                    owner_ally_id NULL     → general: any ally may offer it.
--                    owner_ally_id NOT NULL → exclusive to that ally.
--   ally_products  — what one ally offers of one product (the old
--                    ally_services row, renamed): price + currency,
--                    discount, exclusions, review workflow, publication.
--
-- Backfill: every ally_services row becomes one EXCLUSIVE product of its
-- ally plus the same row renamed to ally_products, keeping its uuid so
-- URLs, benefit_usages, audit rows and shared links keep resolving. Merging
-- duplicates into general products is later curation work.
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. products (master) ───────────────────────────────────────────────────
CREATE TABLE products
(
    products_id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code                   VARCHAR(40)  UNIQUE,
    name                   VARCHAR(200) NOT NULL,
    description            TEXT,
    product_type           VARCHAR(20)  NOT NULL DEFAULT 'SERVICE'
                              CONSTRAINT chk_products_type CHECK (product_type IN ('SERVICE', 'PRODUCT')),
    product_category_id    BIGINT       NOT NULL REFERENCES product_categories (product_categories_id),
    -- NULL = general product; set = exclusive to this ally (enforced on
    -- ally_products by trg_ally_products_exclusive_owner below).
    owner_ally_id          BIGINT       REFERENCES allies (allies_id) ON DELETE CASCADE,
    requires_appointment   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- R2 key of the public catalog image; the mapper derives the URL at read time.
    image_key              VARCHAR(500),

    -- Audit + soft-delete
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    -- Backfill bookkeeping only — dropped at the end of this migration.
    legacy_ally_product_id BIGINT
);

CREATE INDEX idx_products_category    ON products (product_category_id);
CREATE INDEX idx_products_type        ON products (product_type);
CREATE INDEX idx_products_owner_ally  ON products (owner_ally_id) WHERE owner_ally_id IS NOT NULL;
CREATE INDEX idx_products_name_trgm
    ON products USING gin (immutable_unaccent(lower(name)) gin_trgm_ops);

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE products IS
    'Catálogo maestro de productos y servicios (ADR 0026). owner_ally_id NULL = general; con valor = exclusivo de ese aliado.';
COMMENT ON COLUMN products.product_type IS 'SERVICE (consulta, examen, hospedaje…) o PRODUCT (lente, medicamento, repuesto…).';

-- ─── 2. ally_services → ally_products ───────────────────────────────────────
ALTER TABLE ally_services RENAME TO ally_products;
ALTER TABLE ally_products RENAME COLUMN ally_services_id TO ally_products_id;
DO $$
BEGIN
    EXECUTE format('ALTER SEQUENCE %s RENAME TO ally_products_ally_products_id_seq',
                   pg_get_serial_sequence('app.ally_products', 'ally_products_id'));
END $$;
ALTER TRIGGER trg_ally_services_updated_at ON ally_products RENAME TO trg_ally_products_updated_at;

-- Constraint names (explicit chk_* and the auto-generated pkey/key/fkey/check
-- ones) — renaming a pkey/unique constraint renames its index too.
DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'app.ally_products'::regclass AND conname LIKE '%ally_services%'
    LOOP
        EXECUTE format('ALTER TABLE ally_products RENAME CONSTRAINT %I TO %I',
                       c.conname, replace(c.conname, 'ally_services', 'ally_products'));
    END LOOP;
    FOR c IN SELECT indexname FROM pg_indexes
             WHERE schemaname = 'app' AND tablename = 'ally_products' AND indexname LIKE '%ally_services%'
    LOOP
        EXECUTE format('ALTER INDEX %I RENAME TO %I',
                       c.indexname, replace(c.indexname, 'ally_services', 'ally_products'));
    END LOOP;
END $$;

-- ─── 3. Backfill: one exclusive product per existing offer ──────────────────
-- Optical goods and medicines are products; everything else seeded so far
-- (consultations, exams, procedures…) is a service.
INSERT INTO products (name, description, product_type, product_category_id, owner_ally_id,
                      requires_appointment, image_key, is_active, status,
                      created_at, updated_at, created_by, updated_by, legacy_ally_product_id)
SELECT ap.name,
       ap.description,
       CASE WHEN pc.code IN ('LENTES_Y_MONTURAS', 'MEDICAMENTO') THEN 'PRODUCT' ELSE 'SERVICE' END,
       ap.service_category_id,
       ap.ally_id,
       ap.requires_appointment,
       ap.image_key,
       ap.is_active,
       ap.status,
       ap.created_at,
       ap.updated_at,
       ap.created_by,
       ap.updated_by,
       ap.ally_products_id
FROM ally_products ap
         JOIN product_categories pc ON pc.product_categories_id = ap.service_category_id;

ALTER TABLE ally_products ADD COLUMN product_id BIGINT REFERENCES products (products_id);

UPDATE ally_products ap
SET product_id = p.products_id
FROM products p
WHERE p.legacy_ally_product_id = ap.ally_products_id;

ALTER TABLE ally_products ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE products DROP COLUMN legacy_ally_product_id;

-- ─── 4. Columns now owned by the master ─────────────────────────────────────
-- Dropping service_category_id also drops idx_ally_products_category.
ALTER TABLE ally_products
    DROP COLUMN name,
    DROP COLUMN description,
    DROP COLUMN service_category_id,
    DROP COLUMN requires_appointment,
    DROP COLUMN image_key;

ALTER TABLE ally_products
    ADD CONSTRAINT uq_ally_products_ally_product UNIQUE (ally_id, product_id);

CREATE INDEX idx_ally_products_product ON ally_products (product_id);

COMMENT ON TABLE ally_products IS
    'Oferta de un aliado sobre un producto del maestro (antes ally_services, ADR 0026): precio, descuento, exclusiones, revisión y publicación.';

-- ─── 5. Exclusive-owner rule ────────────────────────────────────────────────
-- An exclusive product (owner_ally_id set) can only be offered by its owner,
-- and a product already offered by other allies cannot become exclusive.
CREATE OR REPLACE FUNCTION app.enforce_exclusive_product_owner()
    RETURNS TRIGGER AS $$
DECLARE
    owner BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'ally_products' THEN
        SELECT owner_ally_id INTO owner FROM app.products WHERE products_id = NEW.product_id;
        IF owner IS NOT NULL AND owner <> NEW.ally_id THEN
            RAISE EXCEPTION 'product % is exclusive to ally %', NEW.product_id, owner
                USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_ally_products_exclusive_owner';
        END IF;
    ELSIF NEW.owner_ally_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM app.ally_products
            WHERE product_id = NEW.products_id AND ally_id <> NEW.owner_ally_id) THEN
        RAISE EXCEPTION 'product % is offered by other allies and cannot become exclusive', NEW.products_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_products_exclusive_owner';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ally_products_exclusive_owner
    BEFORE INSERT OR UPDATE OF ally_id, product_id ON ally_products
    FOR EACH ROW EXECUTE FUNCTION app.enforce_exclusive_product_owner();

CREATE TRIGGER trg_products_exclusive_owner
    BEFORE UPDATE OF owner_ally_id ON products
    FOR EACH ROW EXECUTE FUNCTION app.enforce_exclusive_product_owner();

-- ─── 6. ally_service_review_log → ally_product_review_log ───────────────────
ALTER TABLE ally_service_review_log RENAME TO ally_product_review_log;
ALTER TABLE ally_product_review_log RENAME COLUMN ally_service_review_log_id TO ally_product_review_log_id;
ALTER TABLE ally_product_review_log RENAME COLUMN ally_service_id TO ally_product_id;
DO $$
BEGIN
    EXECUTE format('ALTER SEQUENCE %s RENAME TO ally_product_review_log_ally_product_review_log_id_seq',
                   pg_get_serial_sequence('app.ally_product_review_log', 'ally_product_review_log_id'));
END $$;
ALTER INDEX idx_ally_service_review_log_service RENAME TO idx_ally_product_review_log_product;

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'app.ally_product_review_log'::regclass AND conname LIKE '%ally_service%'
    LOOP
        EXECUTE format('ALTER TABLE ally_product_review_log RENAME CONSTRAINT %I TO %I',
                       c.conname, replace(c.conname, 'ally_service', 'ally_product'));
    END LOOP;
END $$;

-- ─── 7. benefit_usages.ally_service_id → ally_product_id ────────────────────
-- Values are unchanged: ally_products kept the ally_services ids.
ALTER TABLE benefit_usages RENAME COLUMN ally_service_id TO ally_product_id;
ALTER INDEX idx_benefit_usages_service_date RENAME TO idx_benefit_usages_ally_product_date;

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'app.benefit_usages'::regclass AND conname LIKE '%ally_service%'
    LOOP
        EXECUTE format('ALTER TABLE benefit_usages RENAME CONSTRAINT %I TO %I',
                       c.conname, replace(c.conname, 'ally_service', 'ally_product'));
    END LOOP;
END $$;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ally_products ap
               LEFT JOIN products p ON p.products_id = ap.product_id
               WHERE p.products_id IS NULL OR p.owner_ally_id IS DISTINCT FROM ap.ally_id) THEN
        RAISE EXCEPTION 'V197: backfill left an offer without its exclusive product';
    END IF;
    IF (SELECT count(*) FROM products) <> (SELECT count(*) FROM ally_products) THEN
        RAISE EXCEPTION 'V197: backfill product count does not match offer count';
    END IF;
END $$;
