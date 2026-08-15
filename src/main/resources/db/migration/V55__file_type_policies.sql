SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V55: file_type_policies — configurable extension/size policy per
-- FileVisibility scope (spec 08-storage-r2.md §2.1).
--
-- One row per visibility. `mode` decides which list FileValidationService
-- checks: ALLOWLIST accepts only extensions listed in `extensions`; DENYLIST
-- rejects only those listed and accepts everything else. This lets an admin
-- flip a scope from "only these are allowed" to "everything except these
-- executables" without a schema change — just an UPDATE on this table.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE file_type_policies
(
    file_type_policies_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    visibility             VARCHAR(20)  NOT NULL UNIQUE
                               CHECK (visibility IN ('PUBLIC', 'TEMPORARY', 'INTERNAL', 'CONFIDENTIAL')),
    mode                   VARCHAR(10)  NOT NULL CHECK (mode IN ('ALLOWLIST', 'DENYLIST')),
    extensions             TEXT[]       NOT NULL,
    max_size_bytes         BIGINT       NOT NULL CHECK (max_size_bytes > 0),

    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by             UUID
);

CREATE TRIGGER trg_file_type_policies_updated_at
    BEFORE UPDATE ON file_type_policies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Defaults — see spec 08-storage-r2.md §2.1 for the reasoning per scope.
INSERT INTO file_type_policies (visibility, mode, extensions, max_size_bytes)
VALUES
    ('PUBLIC',       'ALLOWLIST', ARRAY['jpg', 'jpeg', 'png', 'webp'],       5  * 1024 * 1024),
    ('CONFIDENTIAL', 'ALLOWLIST', ARRAY['pdf', 'jpg', 'jpeg', 'png'],        10 * 1024 * 1024),
    ('TEMPORARY',    'ALLOWLIST', ARRAY['pdf', 'xlsx', 'csv'],               20 * 1024 * 1024),
    ('INTERNAL',     'DENYLIST',  ARRAY['exe', 'sh', 'py', 'bat', 'dll', 'jar'], 20 * 1024 * 1024);
