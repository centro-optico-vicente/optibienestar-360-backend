SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V58: publish/unpublish audit trail for attached_files.
--
-- Backs the publish workflow in spec 08-storage-r2.md §2 (AttachedFileService
-- .publish()/.unpublish()): a row can move from a private visibility
-- (INTERNAL/CONFIDENTIAL) to PUBLIC via a server-side R2 copy, and back again
-- via unpublish(). `visibility='PUBLIC'` alone tells you the current state but
-- not who flipped it or when — `created_by`/`updated_by` are generic audit
-- columns already on this table and get touched by unrelated edits too, so a
-- dedicated pair is needed for this specific business event, the same way
-- ally_services already separates reviewed_by/reviewed_at from its generic
-- audit columns.
--
-- Both nullable: NULL means "never published" (or "currently unpublished",
-- for the *_at half) — unpublish() clears both back to NULL rather than
-- keeping history, since attached_files is not meant to be an audit log; if a
-- full publish/unpublish history is ever needed, that's a separate table.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE attached_files
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN published_by BIGINT REFERENCES users (users_id);
