SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V199: products.is_published — the product master gets the same "may appear
-- publicly" switch allies and ally_products already have (hub ADR 0026).
--
-- Public visibility of an offer now requires all three switches:
--   allies.is_published AND ally_products.is_published AND products.is_published
-- (plus the active flags and review_status = APPROVED on the offer).
-- Lets the admin hide a whole product from the public catalog — every ally's
-- offer of it at once — without touching each offer or deactivating it.
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
    'Puede aparecer en el catálogo público. Una oferta es pública solo si aliado, oferta y producto están publicados (ADR 0026).';
