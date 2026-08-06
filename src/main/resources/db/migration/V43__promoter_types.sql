SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V43: promoter_types — catalog classifying promoters (e.g. INDEPENDIENTE,
-- EMPLEADO, ALIADO). Follows the same shape as ally_types (V11): BIGINT
-- identity PK + uuid, code/name unique, is_active soft-delete, audit columns,
-- set_updated_at trigger, partial is_active index.
--
-- promoters.promoter_type_id is added NULLABLE so existing rows (including
-- the INSTITUCION system promoter) don't need a backfill. The incentives v3
-- engine (commission_tiers, V42) may reference this classification in a
-- future migration to scale commissions per promoter type.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE promoter_types
(
    promoter_types_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid               UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code               VARCHAR(40)  NOT NULL UNIQUE,
    name               VARCHAR(100) NOT NULL UNIQUE,
    description        VARCHAR(200),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID
);

CREATE INDEX idx_promoter_types_is_active ON promoter_types (is_active) WHERE is_active;

CREATE TRIGGER trg_promoter_types_updated_at
    BEFORE UPDATE ON promoter_types
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE promoters
    ADD COLUMN promoter_type_id BIGINT REFERENCES promoter_types (promoter_types_id);

CREATE INDEX idx_promoters_promoter_type_id
    ON promoters (promoter_type_id)
    WHERE promoter_type_id IS NOT NULL;


-- ─── Permission: CATALOG_PROMOTER_TYPE_WRITE ───────────────────────────────
-- Follows V33's per-catalog write key pattern. The V30 trigger auto-grants
-- this to SYSTEM on insert; reads stay on the coarse CATALOG_VIEW_ALL (V34).
INSERT INTO permissions (name, domain_id, description)
SELECT 'CATALOG_PROMOTER_TYPE_WRITE', pd.permission_domains_id, 'Crear, editar y eliminar tipos de promotor'
FROM permission_domains pd
WHERE pd.code = 'CATALOGS';
