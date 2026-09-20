SET search_path TO app, public;

-- ============================================================================
-- V127: campaign-specific report generation permission.
--
-- Campaign reports are a separate business capability and must not inherit the
-- generic REPORT_REPORT_GENERATE permission.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT 'CAMPAIGN_REPORT_GENERATE', permission_domains_id,
       'Generar reportes de campañas de comisiones e incentivos'
FROM permission_domains
WHERE code = 'CAMPAIGN'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name = 'CAMPAIGN_REPORT_GENERATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM permissions WHERE name = 'CAMPAIGN_REPORT_GENERATE'
    ) THEN
        RAISE EXCEPTION 'V127: CAMPAIGN_REPORT_GENERATE was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id
        JOIN permissions p ON p.permissions_id = rp.permission_id
        WHERE r.name = 'SYSTEM'
          AND p.name = 'CAMPAIGN_REPORT_GENERATE'
    ) THEN
        RAISE EXCEPTION 'V127: SYSTEM did not receive CAMPAIGN_REPORT_GENERATE';
    END IF;
END $$;
