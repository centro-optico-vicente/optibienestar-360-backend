SET search_path TO app, public;

-- ============================================================================
-- V195: beneficiary access — every beneficiary can get their own login and use
-- the member portal and the benefits like the holder (titular).
--
-- The holder (member portal, "Mi familia") or staff (admin member detail)
-- grants access to an active beneficiary: the service creates an AFILIADO
-- user linked to the beneficiary's person, with an unusable random password,
-- and emails an activation link (same token mechanism as the password reset,
-- longer TTL). The beneficiary row keeps the link to that user, so the
-- "member context" of the user is the holder's membership resolved through
-- this row. Revoking access (or deactivating the beneficiary) deactivates the
-- user but keeps the link, so a later grant reactivates the same account.
--
-- benefit_usages.beneficiary_id records which beneficiary consumed a benefit
-- at the ally counter (NULL = the holder), so each beneficiary sees their own
-- usage history and the holder sees who used what.
--
-- Permissions (MEMBERS domain): BENEFICIARY_ACCESS_GRANT_OWN /
-- BENEFICIARY_ACCESS_REVOKE_OWN for the holder (AFILIADO) and
-- BENEFICIARY_ACCESS_GRANT / BENEFICIARY_ACCESS_REVOKE for staff
-- (ADMINISTRADOR only by default; OPERADOR gets nothing, assigned from Roles
-- when needed). SYSTEM gets every permission through the V30 trigger; listed
-- explicitly anyway.
-- ============================================================================

-- ─── 1. beneficiaries: link to the beneficiary's own user ───────────────────

ALTER TABLE beneficiaries
    ADD COLUMN user_id           BIGINT      REFERENCES users (users_id),
    ADD COLUMN access_invited_at TIMESTAMPTZ;

-- A user is the login of at most one beneficiary row.
CREATE UNIQUE INDEX uq_beneficiaries_user ON beneficiaries (user_id) WHERE user_id IS NOT NULL;

COMMENT ON COLUMN beneficiaries.user_id IS
    'Usuario propio del beneficiario (rol AFILIADO) para entrar al portal y usar los beneficios como el titular; vacío si nunca se le dio acceso. Al quitar el acceso el usuario se desactiva y el vínculo se conserva';
COMMENT ON COLUMN beneficiaries.access_invited_at IS
    'Último envío del correo de activación de la cuenta del beneficiario';

-- ─── 2. benefit_usages: who consumed the benefit ────────────────────────────

ALTER TABLE benefit_usages
    ADD COLUMN beneficiary_id BIGINT REFERENCES beneficiaries (beneficiaries_id);

CREATE INDEX idx_benefit_usages_beneficiary
    ON benefit_usages (beneficiary_id, usage_date DESC)
    WHERE beneficiary_id IS NOT NULL;

COMMENT ON COLUMN benefit_usages.beneficiary_id IS
    'Beneficiario que usó el beneficio en el aliado; vacío cuando lo usó el titular';

-- ─── 3. Permissions ─────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('BENEFICIARY_ACCESS_GRANT_OWN',  'Dar acceso al portal a un beneficiario propio y reenviarle la invitación (portal del afiliado)'),
    ('BENEFICIARY_ACCESS_REVOKE_OWN', 'Quitar el acceso al portal a un beneficiario propio (portal del afiliado)'),
    ('BENEFICIARY_ACCESS_GRANT',      'Dar acceso al portal a cualquier beneficiario y reenviarle la invitación'),
    ('BENEFICIARY_ACCESS_REVOKE',     'Quitar el acceso al portal a cualquier beneficiario')
) AS v(name, description)
JOIN permission_domains pd ON pd.code = 'MEMBERS'
ON CONFLICT (name) DO NOTHING;

-- Staff side. OPERADOR gets none by default: it is assigned from Roles.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('BENEFICIARY_ACCESS_GRANT', 'BENEFICIARY_ACCESS_REVOKE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Member portal: the holder manages the access of their own beneficiaries.
-- Beneficiary users also carry AFILIADO; the service only lets the holder act.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'AFILIADO')
  AND p.name IN ('BENEFICIARY_ACCESS_GRANT_OWN', 'BENEFICIARY_ACCESS_REVOKE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'app' AND table_name = 'beneficiaries' AND column_name = 'user_id')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = 'app' AND table_name = 'benefit_usages' AND column_name = 'beneficiary_id') THEN
        RAISE EXCEPTION 'V195: beneficiaries.user_id / benefit_usages.beneficiary_id were not created';
    END IF;

    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('BENEFICIARY_ACCESS_GRANT_OWN', 'BENEFICIARY_ACCESS_REVOKE_OWN',
                       'BENEFICIARY_ACCESS_GRANT', 'BENEFICIARY_ACCESS_REVOKE')) <> 4 THEN
        RAISE EXCEPTION 'V195: beneficiary access permissions were not created (MEMBERS domain missing?)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES ('SYSTEM'), ('ADMINISTRADOR')) AS want_role(name)
        CROSS JOIN (VALUES ('BENEFICIARY_ACCESS_GRANT'), ('BENEFICIARY_ACCESS_REVOKE')) AS want_perm(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN roles r ON r.roles_id = rp.role_id AND r.name = want_role.name
            JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = want_perm.name
        )
    ) THEN
        RAISE EXCEPTION 'V195: SYSTEM/ADMINISTRADOR did not receive the staff beneficiary access permissions';
    END IF;

    IF EXISTS (SELECT 1 FROM roles WHERE name = 'AFILIADO') AND EXISTS (
        SELECT 1
        FROM (VALUES ('BENEFICIARY_ACCESS_GRANT_OWN'), ('BENEFICIARY_ACCESS_REVOKE_OWN')) AS want_perm(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'AFILIADO'
            JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = want_perm.name
        )
    ) THEN
        RAISE EXCEPTION 'V195: AFILIADO did not receive the own beneficiary access permissions';
    END IF;

    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'OPERADOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name LIKE 'BENEFICIARY\_ACCESS\_%'
    ) THEN
        RAISE EXCEPTION 'V195: OPERADOR must not receive beneficiary access permissions by default';
    END IF;
END $$;
