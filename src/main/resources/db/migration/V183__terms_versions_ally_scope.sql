-- ====================================================
-- V183__terms_versions_ally_scope.sql
--
-- ALIADO contract templates become scopable. A template may optionally
-- narrow which allies can sign it, by:
--   - ally type   (ally_types, e.g. only "Farmacia" allies),
--   - segment     (B2B "Aliado Comercial" / B2P "Aliado Profesional", V179),
--   - one ally    (a template negotiated for one specific ally).
-- All three NULL = a general template, usable by any ally.
--
-- Each distinct scope combination is its own versioned stream: the
-- "current" version of a stream is still the latest valid_from <= now()
-- (V139 vigency rule), now resolved per (term_type, ally_type_id,
-- segment, ally_id). AFILIADO and PROMOTOR never carry a scope, so their
-- single stream per type is unchanged.
--
-- terms_versions.title already labels a template; no extra name column.
-- ====================================================

SET search_path TO app, public;

ALTER TABLE terms_versions
    ADD COLUMN ally_type_id BIGINT     NULL REFERENCES ally_types (ally_types_id),
    ADD COLUMN segment      VARCHAR(3) NULL,
    ADD COLUMN ally_id      BIGINT     NULL REFERENCES allies (allies_id),
    ADD CONSTRAINT chk_terms_versions_segment
        CHECK (segment IS NULL OR segment IN ('B2B', 'B2P')),
    ADD CONSTRAINT chk_terms_versions_scope_only_aliado
        CHECK (term_type = 'ALIADO'
               OR (ally_type_id IS NULL AND segment IS NULL AND ally_id IS NULL));

-- "Current version of a stream" lookup: latest valid_from <= :at per scope.
CREATE INDEX idx_terms_versions_scope_valid_from
    ON terms_versions (term_type, ally_type_id, segment, ally_id, valid_from DESC);

COMMENT ON COLUMN terms_versions.ally_type_id IS
    'Solo ALIADO: plantilla restringida a aliados de este tipo; NULL = cualquier tipo';
COMMENT ON COLUMN terms_versions.segment IS
    'Solo ALIADO: plantilla restringida al segmento B2B (Comercial) o B2P (Profesional); NULL = cualquier segmento';
COMMENT ON COLUMN terms_versions.ally_id IS
    'Solo ALIADO: plantilla hecha para un aliado específico; NULL = cualquier aliado';
COMMENT ON COLUMN terms_versions.is_public IS
    'Visible en el endpoint público de la web (GET /v1/public/terms/{type}); NO decide si se pide aceptación';
