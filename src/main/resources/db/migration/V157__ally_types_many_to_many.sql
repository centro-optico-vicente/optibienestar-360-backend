SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V157: convert `ally_types` from a single required FK on `allies` into a
-- M:N relation, so an ally can hold multiple business types (e.g. a Farmacia
-- that expands into Laboratorio too). `ally_types`/`AllyType` keep their
-- name — only the relationship to `allies` changes.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE ally_ally_types
(
    ally_ally_types_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ally_id            BIGINT      NOT NULL REFERENCES allies (allies_id) ON DELETE CASCADE,
    ally_type_id       BIGINT      NOT NULL REFERENCES ally_types (ally_types_id),
    is_active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    UNIQUE (ally_id, ally_type_id)
);

CREATE INDEX idx_ally_ally_types_ally_type ON ally_ally_types (ally_type_id);

-- Backfill: one row per existing ally, carrying over its current single type.
INSERT INTO ally_ally_types (ally_id, ally_type_id, created_at, created_by)
SELECT allies_id, ally_type_id, created_at, created_by FROM allies;

ALTER TABLE allies DROP CONSTRAINT allies_ally_type_id_fkey;
DROP INDEX IF EXISTS idx_allies_ally_type;
ALTER TABLE allies DROP COLUMN ally_type_id;
