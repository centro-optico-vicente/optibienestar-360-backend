SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V67: system_configs table
--
-- system_configs stores global system-wide configuration settings as a single-row
-- active record (singleton pattern, mirroring security_policies).
-- Initial field: `report_footer` (text displayed on generated PDF documents).
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE system_configs
(
    system_configs_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid              UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    report_footer     TEXT,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);

CREATE TRIGGER trg_system_configs_updated_at
    BEFORE UPDATE ON system_configs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Initial singleton record
INSERT INTO system_configs (is_active)
VALUES (TRUE);
