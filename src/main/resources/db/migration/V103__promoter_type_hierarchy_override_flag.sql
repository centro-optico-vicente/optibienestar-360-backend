SET search_path TO app, public;

-- ============================================================================
-- V103: per-promoter-type toggle for the hierarchy override cascade (hub plan
-- ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §2, "Punto de
-- extensión documentado" — pregunta 7 del hub notes: ¿los Independientes
-- quedan exentos de override jerárquico?).
--
-- Nothing is confirmed with the business yet, so this ships as a
-- configurable flag rather than a hardcoded exemption: default TRUE (every
-- promoter type keeps generating overrides up its chain, today's behavior
-- unchanged) — an admin can flip it per type (e.g. INDEPENDIENTE) once the
-- business confirms the exemption, with zero code changes.
-- ============================================================================

ALTER TABLE promoter_types
    ADD COLUMN generates_hierarchy_override BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN promoter_types.generates_hierarchy_override IS
    'FALSE = a commission earned by a promoter of this type never cascades a '
    'hierarchy override up to their supervisor/coordinador chain (V102). '
    'Default TRUE preserves today''s behavior for every existing type.';
