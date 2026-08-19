SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V62: data_change_audit_log — generic create/update/delete trail captured by
-- DataChangeAuditAspect (spec 07-audit.md §Decisiones 1, 3). Insert-only.
--
-- `restored_from_id` prepares a future restore feature (not implemented in
-- this deliverable, see <DOMAIN>_AUDIT_RESTORE in V66): a restore would be
-- modeled as a new UPDATE row whose after_json is the restored state,
-- pointing back at the historical row that originated it — no separate
-- mechanism needed.
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE data_change_audit_log
(
    data_change_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    entity_key               VARCHAR(80)  NOT NULL,
    entity_id                BIGINT,
    entity_uuid              UUID,
    action                   VARCHAR(20)  NOT NULL
        CONSTRAINT chk_data_change_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE')),
    before_json              JSONB,
    after_json               JSONB,
    actor_id                 BIGINT       REFERENCES users (users_id),
    login_audit_log_id       BIGINT       REFERENCES login_audit_log (login_audit_log_id),
    request_method           VARCHAR(10),
    request_path             VARCHAR(255),
    restored_from_id         BIGINT       REFERENCES data_change_audit_log (data_change_audit_log_id),
    occurred_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_data_change_entity      ON data_change_audit_log (entity_key, entity_uuid);
CREATE INDEX idx_data_change_entity_id   ON data_change_audit_log (entity_key, entity_id);
CREATE INDEX idx_data_change_actor       ON data_change_audit_log (actor_id);
CREATE INDEX idx_data_change_occurred_at ON data_change_audit_log (occurred_at DESC);
CREATE INDEX idx_data_change_login_audit ON data_change_audit_log (login_audit_log_id);
