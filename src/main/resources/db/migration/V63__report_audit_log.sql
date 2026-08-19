SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V63: report_audit_log — who generated which report, with what parameters,
-- and a reference to the resulting file (spec 07-audit.md §Decisiones 4).
--
-- Requires GenericDocumentController to start persisting generated documents
-- to R2/attached_files (today it only streams bytes back). If the upload
-- fails the HTTP response still delivers the file; the log row is kept with
-- attached_file_id = NULL (see spec §Reportes).
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE report_audit_log
(
    report_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    report_type          VARCHAR(80)  NOT NULL,
    entity_key           VARCHAR(80),
    entity_id            BIGINT,
    entity_uuid          UUID,
    entity_identifier    VARCHAR(120),
    format                VARCHAR(10)  NOT NULL
        CONSTRAINT chk_report_audit_format CHECK (format IN ('PDF', 'XLSX')),
    parameters_json       JSONB,
    actor_id              BIGINT       REFERENCES users (users_id),
    login_audit_log_id    BIGINT       REFERENCES login_audit_log (login_audit_log_id),
    attached_file_id      BIGINT       REFERENCES attached_files (attached_files_id),
    file_name             VARCHAR(255),
    size_bytes            BIGINT,
    generated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_audit_actor        ON report_audit_log (actor_id);
CREATE INDEX idx_report_audit_generated_at ON report_audit_log (generated_at DESC);
CREATE INDEX idx_report_audit_entity       ON report_audit_log (entity_key, entity_identifier);
CREATE INDEX idx_report_audit_entity_id    ON report_audit_log (entity_key, entity_id);
CREATE INDEX idx_report_audit_file         ON report_audit_log (attached_file_id);
