SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V65: report_share — schema prepared for sharing a generated report via a
-- token-based link (spec 07-audit.md §Decisiones 7). Functionality is NOT
-- implemented in this deliverable; only the table and the REPORT_SHARE
-- permission (V66) are put in place ahead of time.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE report_share
(
    report_share_id      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    report_audit_log_id  BIGINT       NOT NULL REFERENCES report_audit_log (report_audit_log_id),
    share_token          VARCHAR(120) NOT NULL UNIQUE,
    share_link           VARCHAR(500),
    recipients           JSONB,
    expires_at           TIMESTAMPTZ  NOT NULL,
    revoked_at           TIMESTAMPTZ,
    created_by           BIGINT       REFERENCES users (users_id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_report_share_report  ON report_share (report_audit_log_id);
CREATE INDEX idx_report_share_token   ON report_share (share_token);
CREATE INDEX idx_report_share_expires ON report_share (expires_at);
