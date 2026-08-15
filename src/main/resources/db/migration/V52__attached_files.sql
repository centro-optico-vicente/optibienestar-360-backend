SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V52: attached_files — generic polymorphic file-attachment mechanism.
--
-- Backs spec 08-storage-r2.md §4. Instead of a bespoke entity+table per domain
-- (the member_documents pattern from V17), any table in the system can attach
-- files to one of its rows by referencing it via (owner_table, owner_uuid) —
-- no FK possible across arbitrary tables, so referential integrity for the
-- owner is enforced at the application layer, not here.
--
-- `visibility` drives which R2 bucket/key-prefix the file lives under
-- (see StorageKeyBuilder — key = "{visibility}/{owner_table}/{owner_uuid}/{file_name}"):
--   PUBLIC       -> public bucket, no presign needed (catalog images, etc.)
--   CONFIDENTIAL -> private bucket, presigned URL required (member/ally docs)
--   INTERNAL     -> private bucket, presigned URL, staff-only, no owner-level check
--   TEMPORARY    -> private bucket, short-lived (future reporting engine, ADR 0012)
--
-- `shared` is a business-level ACL, orthogonal to `visibility` (which is a
-- storage concern): a freshly uploaded file is visible only to its uploader
-- (or to a caller holding the domain's *_DOCUMENT_VIEW_ALL authority) until
-- someone with upload or delete rights flips it to shared=true, at which point
-- anyone with *_DOCUMENT_VIEW_OWN on that domain can see it too.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE attached_files
(
    attached_files_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    -- Polymorphic owner — e.g. ('members', member.uuid), ('commissions', commission.uuid).
    owner_table       VARCHAR(50)  NOT NULL,
    owner_uuid        UUID         NOT NULL,

    visibility        VARCHAR(20)  NOT NULL
                          CHECK (visibility IN ('PUBLIC', 'TEMPORARY', 'INTERNAL', 'CONFIDENTIAL')),

    -- Free-form tag within the owning domain (e.g. 'ID_FRONT', 'CONTRACT',
    -- 'BANK_TRANSFER_RECEIPT'). Not constrained here — each domain's service
    -- layer defines and validates its own set of expected categories.
    category          VARCHAR(50)  NOT NULL,

    -- File storage — R2 key in `file_key`. Name/size/mime kept for the
    -- download surface and for FileValidationService checks.
    file_key          VARCHAR(500) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),

    uploaded_by       BIGINT       REFERENCES users (users_id),
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Business-level sharing ACL — see header comment.
    shared            BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Audit + soft-delete
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);

CREATE INDEX idx_attached_files_owner    ON attached_files (owner_table, owner_uuid);
CREATE INDEX idx_attached_files_uploader ON attached_files (uploaded_by);

CREATE TRIGGER trg_attached_files_updated_at
    BEFORE UPDATE ON attached_files
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
