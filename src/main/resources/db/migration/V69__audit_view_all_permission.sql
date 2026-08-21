SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V69: AUDIT_VIEW_ALL — the permission that gates the new cross-entity admin
-- endpoint GET /v1/admin/audit/data-changes.
--
-- V66 seeded <DOMAIN>_AUDIT_VIEW per domain (ALLY_AUDIT_VIEW, MEMBER_AUDIT_VIEW,
-- ...) for a FUTURE per-record "this ally's change history" endpoint scoped to
-- its own domain permission. Neither those nor V64's AUDIT_VIEW_LOGIN (login
-- history) fit a single screen that lists data_change_audit_log rows across
-- EVERY entity_key at once — that needs its own domain-agnostic permission,
-- same rationale as AUDIT_VIEW_LOGIN/AUDIT_MANAGE_CONFIG in V64.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT 'AUDIT_VIEW_ALL', pd.permission_domains_id, 'Ver la bitácora de cambios de datos de todas las entidades'
FROM permission_domains pd
WHERE pd.code = 'AUDIT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'AUDIT_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'AUDIT_VIEW_ALL') THEN
        RAISE EXCEPTION 'V69: AUDIT_VIEW_ALL was not created';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'AUDIT_VIEW_ALL'
    ) THEN
        RAISE EXCEPTION 'V69: SYSTEM did not receive AUDIT_VIEW_ALL (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'AUDIT_VIEW_ALL'
    ) THEN
        RAISE EXCEPTION 'V69: ADMINISTRADOR did not receive AUDIT_VIEW_ALL';
    END IF;
END $$;
