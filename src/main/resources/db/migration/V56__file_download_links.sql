SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V56: file_download_links — shareable download link with its own business
-- expiration (days), decoupled from the R2 presigned URL TTL (minutes, and
-- capped at 7 days by R2 itself). Spec 08-storage-r2.md §6.
--
-- The token in this table is what gets emailed/shown to a user; it is never
-- the presigned URL itself. `GET /v1/files/links/{token}` looks up this row,
-- checks it is still valid, and mints a fresh short-lived presigned URL
-- against `file_key` on every hit — so a link "valid for 10 days" keeps
-- working even though no single presigned URL lives that long.
--
-- Not yet wired to any producer (the reporting engine, ADR 0012, is out of
-- scope for this vertical) — this is the storage-layer piece kept ready.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE file_download_links
(
    file_download_links_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    visibility               VARCHAR(20)  NOT NULL
                                 CHECK (visibility IN ('PUBLIC', 'TEMPORARY', 'INTERNAL', 'CONFIDENTIAL')),
    resource_table           VARCHAR(50)  NOT NULL,
    resource_uuid            UUID         NOT NULL,
    file_key                 VARCHAR(500) NOT NULL,

    -- Business validity — can be days, unlike the R2 presigned URL TTL.
    expires_at               TIMESTAMPTZ  NOT NULL,
    revoked_at               TIMESTAMPTZ,

    max_downloads            INT          CHECK (max_downloads IS NULL OR max_downloads > 0),
    download_count           INT          NOT NULL DEFAULT 0,

    created_by               BIGINT       REFERENCES users (users_id),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_file_download_links_resource ON file_download_links (resource_table, resource_uuid);
