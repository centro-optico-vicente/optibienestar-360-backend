SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V201: calificación de consumos por el afiliado.
--
-- Cada consumo (benefit_usages) se puede calificar UNA vez — editable — con
-- estrellas 1..5 para el aliado y, si el consumo es de una oferta catalogada,
-- para el producto/servicio. La calificación vive en la propia fila del
-- consumo: es 1:1, y así "solo califica quien consumió" queda garantizado por
-- construcción (no hay votos sin consumo detrás).
--
-- Los promedios se desnormalizan en allies / ally_products / products para que
-- el directorio y el catálogo públicos no agreguen en cada listado (mismo
-- criterio que logo_url, V200). Los mantiene BenefitUsageRatingsService al
-- calificar; nunca se editan a mano. Solo cuentan consumos vigentes
-- (is_active AND status = 'REGISTERED').
--
--   allies.rating_*         → ally_rating de todos sus consumos
--   ally_products.rating_*  → product_rating de los consumos de esa oferta
--   products.rating_*       → product_rating de todas sus ofertas (cross-aliado)
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. Calificación en el consumo ──────────────────────────────────────────
ALTER TABLE benefit_usages
    ADD COLUMN ally_rating    SMALLINT,
    ADD COLUMN product_rating SMALLINT,
    ADD COLUMN rating_comment VARCHAR(500),
    ADD COLUMN rated_at       TIMESTAMPTZ;

ALTER TABLE benefit_usages
    ADD CONSTRAINT chk_benefit_usages_ally_rating_range
        CHECK (ally_rating IS NULL OR ally_rating BETWEEN 1 AND 5),
    ADD CONSTRAINT chk_benefit_usages_product_rating_range
        CHECK (product_rating IS NULL OR product_rating BETWEEN 1 AND 5),
    -- Sin oferta catalogada no hay producto que calificar.
    ADD CONSTRAINT chk_benefit_usages_product_rating_needs_product
        CHECK (product_rating IS NULL OR ally_product_id IS NOT NULL),
    -- Calificar = al menos el aliado; rated_at marca que la fila fue calificada.
    ADD CONSTRAINT chk_benefit_usages_rating_paired
        CHECK ((rated_at IS NULL AND ally_rating IS NULL AND product_rating IS NULL AND rating_comment IS NULL)
            OR (rated_at IS NOT NULL AND ally_rating IS NOT NULL));

COMMENT ON COLUMN benefit_usages.ally_rating IS
    'Estrellas 1..5 que el afiliado dio al aliado por este consumo. NULL = sin calificar.';
COMMENT ON COLUMN benefit_usages.product_rating IS
    'Estrellas 1..5 al producto/servicio consumido. Solo cuando hay ally_product_id.';
COMMENT ON COLUMN benefit_usages.rated_at IS
    'Última vez que el afiliado calificó (o editó la calificación de) este consumo.';

-- Recalcular los promedios filtra por aliado / oferta sobre filas calificadas.
CREATE INDEX idx_benefit_usages_ally_rated
    ON benefit_usages (ally_id) WHERE rated_at IS NOT NULL AND is_active;
CREATE INDEX idx_benefit_usages_ally_product_rated
    ON benefit_usages (ally_product_id) WHERE product_rating IS NOT NULL AND is_active;

-- ─── 2. Promedios desnormalizados ───────────────────────────────────────────
ALTER TABLE allies
    ADD COLUMN rating_avg   NUMERIC(3, 2),
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;
ALTER TABLE ally_products
    ADD COLUMN rating_avg   NUMERIC(3, 2),
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;
ALTER TABLE products
    ADD COLUMN rating_avg   NUMERIC(3, 2),
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN allies.rating_avg IS
    'Promedio de ally_rating de sus consumos vigentes; NULL sin votos. Lo sincroniza el servicio de calificaciones.';
COMMENT ON COLUMN ally_products.rating_avg IS
    'Promedio de product_rating de los consumos vigentes de esta oferta; NULL sin votos.';
COMMENT ON COLUMN products.rating_avg IS
    'Promedio de product_rating en todas las ofertas del producto; NULL sin votos.';

-- ─── 3. Permiso ─────────────────────────────────────────────────────────────
-- SYSTEM lo recibe por el trigger de V30.
INSERT INTO permissions (name, domain_id, description)
SELECT 'MEMBER_RATE_USAGE', pd.permission_domains_id, 'Calificar los consumos propios (aliado y producto)'
FROM permission_domains pd
WHERE pd.code = 'MEMBERS';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'AFILIADO'
  AND p.name = 'MEMBER_RATE_USAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'MEMBER_RATE_USAGE') THEN
        RAISE EXCEPTION 'V201: MEMBER_RATE_USAGE was not created';
    END IF;
END $$;
