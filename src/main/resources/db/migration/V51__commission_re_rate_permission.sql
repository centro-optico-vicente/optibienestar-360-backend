SET search_path TO app, public;

-- V51: permission for the month-close retroactive re-rating action
-- (vertical-8 Ítem A, POST /v1/admin/commissions/re-rate).

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COMMISSION_RE_RATE', 'COMMISSIONS', 'Re-ratear retroactivamente comisiones INSCRIPTION pendientes al cierre de mes')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'COMMISSION_RE_RATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'COMMISSION_RE_RATE') THEN
        RAISE EXCEPTION 'V51: COMMISSION_RE_RATE was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name = 'COMMISSION_RE_RATE'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V51: SYSTEM did not receive COMMISSION_RE_RATE (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'COMMISSION_RE_RATE'
    ) THEN
        RAISE EXCEPTION 'V51: ADMINISTRADOR did not receive COMMISSION_RE_RATE';
    END IF;
END $$;
