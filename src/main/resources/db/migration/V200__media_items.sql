SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V200: generic media (hub ADR 0027).
--
-- media_items is an ordered image list per owner (ally, product, ally offer;
-- users / members / advisors later) with ONE primary image. Each image is an
-- uploaded file (public_key, in the public R2 bucket) or an external https URL.
--
-- Every owner table carries `logo_url`: the effective URL of its primary image,
-- denormalized so lists never join media_items. It is kept in sync by
-- MediaItemsService (never edited by hand). allies.logo_url already exists (V11).
--
-- owner_table has NO CHECK on the table list on purpose (same polymorphic
-- pattern as attached_files): new owners need no migration; the Java
-- MediaOwner registry is the allowlist.
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. media_items ─────────────────────────────────────────────────────────
CREATE TABLE media_items
(
    media_items_id   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid             UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    owner_table      VARCHAR(50)  NOT NULL,
    owner_uuid       UUID         NOT NULL,
    attached_file_id BIGINT       REFERENCES attached_files (attached_files_id) ON DELETE SET NULL,
    public_key       VARCHAR(500),
    external_url     VARCHAR(500),
    caption          VARCHAR(200),
    sort_order       INT          NOT NULL DEFAULT 0,
    is_primary       BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Audit + soft-delete
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    status           VARCHAR(50),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,

    CONSTRAINT chk_media_items_one_source CHECK ((public_key IS NULL) <> (external_url IS NULL))
);

-- At most one active primary image per owner.
CREATE UNIQUE INDEX uq_media_items_one_primary
    ON media_items (owner_table, owner_uuid) WHERE is_primary AND is_active;
CREATE INDEX idx_media_items_owner
    ON media_items (owner_table, owner_uuid, sort_order) WHERE is_active;

CREATE TRIGGER trg_media_items_updated_at
    BEFORE UPDATE ON media_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE media_items IS
    'Lista ordenada de imágenes de un dueño (aliado, producto, oferta…), con una principal; archivo en R2 o URL externa (ADR 0027).';

-- ─── 2. logo_url on every owner table ───────────────────────────────────────
ALTER TABLE products      ADD COLUMN logo_url VARCHAR(500);
ALTER TABLE ally_products ADD COLUMN logo_url VARCHAR(500);

COMMENT ON COLUMN products.logo_url IS
    'URL de la imagen principal (media_items), sincronizada por el servicio de media; no editar a mano (ADR 0027).';
COMMENT ON COLUMN ally_products.logo_url IS
    'URL de la imagen principal propia de la oferta (media_items); NULL = usa la del producto (ADR 0027).';

-- ─── 3. Backfill: allies.logo_url → primary external image ──────────────────
-- Skips blank values and non-https URLs (the API only accepts https from now
-- on); the column itself is left untouched, so nothing already shown changes.
INSERT INTO media_items (owner_table, owner_uuid, external_url, sort_order, is_primary, created_at, updated_at)
SELECT 'allies', a.uuid, btrim(a.logo_url), 0, TRUE, a.created_at, a.updated_at
FROM allies a
WHERE a.logo_url IS NOT NULL
  AND btrim(a.logo_url) ~* '^https://'
  AND length(btrim(a.logo_url)) <= 500;

-- ─── 4. Backfill: products.image_key → primary uploaded image ───────────────
-- The attached file keeps its R2 object; V197/V198 already point it at the
-- product (owner_table = 'products'). Its category moves to MEDIA.
INSERT INTO media_items (owner_table, owner_uuid, attached_file_id, public_key, sort_order, is_primary,
                         created_at, updated_at)
SELECT 'products', p.uuid, af.attached_files_id, p.image_key, 0, TRUE, p.created_at, p.updated_at
FROM products p
         LEFT JOIN LATERAL (
    SELECT f.attached_files_id
    FROM attached_files f
    WHERE f.owner_table = 'products' AND f.owner_uuid = p.uuid AND f.category = 'CATALOG_IMAGE' AND f.is_active
    ORDER BY f.uploaded_at DESC
    LIMIT 1
    ) af ON TRUE
WHERE p.image_key IS NOT NULL;

UPDATE attached_files SET category = 'MEDIA'
WHERE owner_table IN ('products', 'ally_products', 'allies') AND category = 'CATALOG_IMAGE';

-- products.logo_url of the uploaded images needs publicBaseUrl, which SQL does
-- not know: MediaLogoUrlSyncRunner fills it on startup. image_key goes away.
ALTER TABLE products DROP COLUMN image_key;

-- ─── 5. Permissions ─────────────────────────────────────────────────────────
-- Granular per action. UPDATE covers caption, primary and order. Granted to the
-- roles that already hold the equivalent authority; SYSTEM gets them through
-- the V30 trigger. The ally portal is membership-based and needs none.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ALLY_IMAGE_UPLOAD',         'ALLIES',   'Subir imágenes o agregar URLs a la galería de un aliado'),
    ('ALLY_IMAGE_UPDATE',         'ALLIES',   'Editar texto, imagen principal y orden de la galería de un aliado'),
    ('ALLY_IMAGE_DELETE',         'ALLIES',   'Eliminar imágenes de la galería de un aliado'),
    ('ALLY_PRODUCT_IMAGE_UPLOAD', 'ALLIES',   'Subir imágenes o agregar URLs a la galería de una oferta de aliado'),
    ('ALLY_PRODUCT_IMAGE_UPDATE', 'ALLIES',   'Editar texto, imagen principal y orden de la galería de una oferta'),
    ('ALLY_PRODUCT_IMAGE_DELETE', 'ALLIES',   'Eliminar imágenes de la galería de una oferta de aliado'),
    ('PRODUCT_IMAGE_UPDATE',      'PRODUCTS', 'Editar texto, imagen principal y orden de la galería de un producto')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM (VALUES
    ('ALLY_UPDATE',             'ALLY_IMAGE_UPLOAD'),
    ('ALLY_UPDATE',             'ALLY_IMAGE_UPDATE'),
    ('ALLY_UPDATE',             'ALLY_IMAGE_DELETE'),
    ('ALLY_PRODUCT_UPDATE',     'ALLY_PRODUCT_IMAGE_UPLOAD'),
    ('ALLY_PRODUCT_UPDATE',     'ALLY_PRODUCT_IMAGE_UPDATE'),
    ('ALLY_PRODUCT_UPDATE',     'ALLY_PRODUCT_IMAGE_DELETE'),
    ('PRODUCT_IMAGE_UPLOAD',    'PRODUCT_IMAGE_UPDATE')
) AS m(existing, granted)
JOIN permissions ep ON ep.name = m.existing
JOIN role_permissions rp ON rp.permission_id = ep.permissions_id
JOIN permissions np ON np.name = m.granted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── 6. Audit ───────────────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name)
VALUES ('media_item', 'Imágenes', 'media_items')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF (SELECT count(*) FROM media_items WHERE owner_table = 'products')
       <> (SELECT count(*) FROM media_items WHERE owner_table = 'products' AND public_key IS NOT NULL) THEN
        RAISE EXCEPTION 'V200: product media rows without a public key';
    END IF;
    IF EXISTS (SELECT 1 FROM permissions WHERE name = 'PRODUCT_IMAGE_UPDATE') IS FALSE THEN
        RAISE EXCEPTION 'V200: PRODUCT_IMAGE_UPDATE missing';
    END IF;
END $$;
