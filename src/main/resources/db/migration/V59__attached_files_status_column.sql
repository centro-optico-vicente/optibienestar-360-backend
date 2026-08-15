SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V59: attached_files.status — the entity is about to extend BaseEntity
-- (uuid/is_active/created_at/updated_at/created_by/updated_by, the same
-- superclass every other entity in the codebase uses), which requires a
-- `status` column. V52 omitted it; added here rather than special-casing
-- AttachedFile to skip BaseEntity.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE attached_files
    ADD COLUMN status VARCHAR(50);
