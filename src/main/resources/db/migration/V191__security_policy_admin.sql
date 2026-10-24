SET search_path TO app, public;

-- ============================================================
-- V191: admin view/update of the active auth security policy
-- (GET/PUT /v1/admin/security-policy).
--
-- 1) SECURITY_POLICY permission domain + SECURITY_POLICY_VIEW /
--    SECURITY_POLICY_UPDATE (one per action, no _CREATE/_DELETE: the policy
--    is a singleton, same reasoning as ORGANIZATION in V98). Granted to
--    SYSTEM (V30 trigger + explicit insert) and ADMINISTRADOR, matching how
--    other sensitive tenant-wide config is granted (V98 organization,
--    V64 AUDIT_MANAGE_CONFIG). No SYSTEM-only guard.
--
-- 2) CHECK constraints mirroring SecurityPolicyUpdateRequest:
--      max_login_attempts       >= 1  (0 would lock on the first failure)
--      lockout_duration_minutes >= 1  (0 would silently disable the lockout)
--      days_password_expires    >= 0  (0 = new passwords get no expiry date)
--      password_history_count   >= 0  (0 = history check disabled, reuse allowed)
--      max_concurrent_sessions  >= 1
--    The V6 seed (5, 30, 90, 5, 3) satisfies all of them.
--
-- 3) entity_config row so @Auditable(entity = "security_policy") is not a
--    silent no-op (see V100 for why a missing row disables auditing).
-- ============================================================

-- ─── 1) Permissions ─────────────────────────────────────────────────────────
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('SECURITY_POLICY', 'Política de seguridad', 'i-lucide-lock-keyhole',
        'Intentos de inicio de sesión, bloqueo, expiración e historial de contraseñas', 16)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('SECURITY_POLICY_VIEW',   'SECURITY_POLICY', 'Ver la política de seguridad de acceso'),
    ('SECURITY_POLICY_UPDATE', 'SECURITY_POLICY', 'Actualizar la política de seguridad de acceso')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('SECURITY_POLICY_VIEW', 'SECURITY_POLICY_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── 2) CHECK constraints ───────────────────────────────────────────────────
ALTER TABLE security_policies
    ADD CONSTRAINT chk_security_policies_max_login_attempts       CHECK (max_login_attempts >= 1),
    ADD CONSTRAINT chk_security_policies_lockout_duration_minutes CHECK (lockout_duration_minutes >= 1),
    ADD CONSTRAINT chk_security_policies_days_password_expires    CHECK (days_password_expires >= 0),
    ADD CONSTRAINT chk_security_policies_password_history_count   CHECK (password_history_count >= 0),
    ADD CONSTRAINT chk_security_policies_max_concurrent_sessions  CHECK (max_concurrent_sessions >= 1);

-- ─── 3) Audit registration ──────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name)
VALUES ('security_policy', 'Política de seguridad', 'security_policies')
ON CONFLICT (entity_key) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permission_domains WHERE code = 'SECURITY_POLICY') THEN
        RAISE EXCEPTION 'V191: SECURITY_POLICY permission domain was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES ('SECURITY_POLICY_VIEW'), ('SECURITY_POLICY_UPDATE')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V191: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('SECURITY_POLICY_VIEW', 'SECURITY_POLICY_UPDATE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V191: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('SECURITY_POLICY_VIEW', 'SECURITY_POLICY_UPDATE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V191: ADMINISTRADOR did not receive the new permissions';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM entity_config WHERE entity_key = 'security_policy') THEN
        RAISE EXCEPTION 'V191: entity_config row for security_policy was not created';
    END IF;
END $$;
