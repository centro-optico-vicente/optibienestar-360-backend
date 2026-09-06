SET search_path TO app, public;

-- V95: permission for voiding a single PENDING commission (excludes it from
-- the next payout without touching the rest of the promoter's period) —
-- AdminCommissionController's mutations were split off "once the payout
-- flow lands" (see its class javadoc); this is the void half of that.

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COMMISSION_VOID', 'COMMISSIONS', 'Anular una comisión pendiente antes de su liquidación')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'COMMISSION_VOID'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'COMMISSION_VOID') THEN
        RAISE EXCEPTION 'V95: COMMISSION_VOID was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name = 'COMMISSION_VOID'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V95: SYSTEM did not receive COMMISSION_VOID (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'COMMISSION_VOID'
    ) THEN
        RAISE EXCEPTION 'V95: ADMINISTRADOR did not receive COMMISSION_VOID';
    END IF;
END $$;
