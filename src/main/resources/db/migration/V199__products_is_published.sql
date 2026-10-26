SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V199: products.is_published — whether the product is listed in the public
-- cross-ally catalog (hub ADR 0026), alongside the switches allies and
-- ally_products already have.
--
--   allies.is_published + ally_products.is_published → the ally publishes the
--     offer: it shows on that ally's public page.
--   + products.is_published → the product also shows in the catalog and in the
--     product comparison across allies.
--
-- An unpublished product is therefore still public through each ally that
-- publishes its offer; it just isn't listed in the general catalog.
--
-- Existing products start published so the current public catalog does not
-- change; published_at mirrors the allies / ally_products columns.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE products
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN published_at TIMESTAMPTZ;

UPDATE products SET published_at = created_at;

CREATE INDEX idx_products_published ON products (products_id) WHERE is_published AND is_active;

COMMENT ON COLUMN products.is_published IS
    'Aparece en el catálogo general y en la comparativa entre aliados. Sin publicar, se ve solo desde cada aliado que publica su oferta (ADR 0026).';
