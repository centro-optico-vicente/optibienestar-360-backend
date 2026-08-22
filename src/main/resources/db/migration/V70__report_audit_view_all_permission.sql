SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V70: REPORT_AUDIT_VIEW_ALL — the permission that gates the new cross-entity
-- admin endpoint GET /v1/admin/audit/reports, mirroring V69's AUDIT_VIEW_ALL
-- but for report_audit_log instead of data_change_audit_log.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT 'REPORT_AUDIT_VIEW_ALL', pd.permission_domains_id, 'Ver la bitácora de generación de reportes de todas las entidades'
FROM permission_domains pd
WHERE pd.code = 'AUDIT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'REPORT_AUDIT_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'REPORT_AUDIT_VIEW_ALL') THEN
        RAISE EXCEPTION 'V70: REPORT_AUDIT_VIEW_ALL was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'REPORT_AUDIT_VIEW_ALL'
    ) THEN
        RAISE EXCEPTION 'V70: SYSTEM did not receive REPORT_AUDIT_VIEW_ALL (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'REPORT_AUDIT_VIEW_ALL'
    ) THEN
        RAISE EXCEPTION 'V70: ADMINISTRADOR did not receive REPORT_AUDIT_VIEW_ALL';
    END IF;
END $$;
