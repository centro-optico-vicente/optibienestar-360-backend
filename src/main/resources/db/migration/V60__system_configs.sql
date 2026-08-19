SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V60: system_configs table & REPORT_PRINT permission
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

-- ─── Permission: REPORT_PRINT under REPORTS domain ─────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT 'REPORT_PRINT', pd.permission_domains_id, 'Imprimir y generar reportes o fichas genéricas en PDF/XLSX'
FROM permission_domains pd
WHERE pd.code = 'REPORTS'
ON CONFLICT (name) DO NOTHING;

-- Grant to ADMINISTRADOR, OPERADOR, OPERADOR_MEDICO
-- (SYSTEM receives it automatically via V30 trigger)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('ADMINISTRADOR', 'OPERADOR', 'OPERADOR_MEDICO')
  AND p.name = 'REPORT_PRINT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Verification assertion
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'REPORT_PRINT') THEN
        RAISE EXCEPTION 'V60: REPORT_PRINT permission was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'REPORT_PRINT'
    ) THEN
        RAISE EXCEPTION 'V60: SYSTEM did not receive REPORT_PRINT permission';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'REPORT_PRINT'
    ) THEN
        RAISE EXCEPTION 'V60: ADMINISTRADOR did not receive REPORT_PRINT permission';
    END IF;
END $$;
