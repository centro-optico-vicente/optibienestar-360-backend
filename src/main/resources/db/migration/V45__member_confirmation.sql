SET search_path TO app, public;

-- V45: affiliate confirmation date + admin manual confirmation (project chat 2026-08-06).
--
-- confirmed_at is normally stamped automatically the moment the member's
-- first payment is APPROVED (PaymentsService.approve). Some affiliates never
-- pay because they are fully covered by a subsidy (subsidy module, V41), so
-- admins also need a manual confirmation action — hence the dedicated
-- MEMBER_CONFIRM permission rather than piggy-backing on payment approval.

ALTER TABLE members
    ADD COLUMN confirmed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN members.confirmed_at IS
    'Set automatically on the member''s first approved payment, or manually by an admin (MEMBER_CONFIRM) for subsidized members that never pay. NULL = pending confirmation.';


-- ─── Permission (MEMBERS domain) ─────────────────────────────────────────────
-- The V30 trigger auto-grants it to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('MEMBER_CONFIRM', 'MEMBERS', 'Confirmar manualmente a un afiliado (p. ej. cubierto por subsidio, sin pagos)')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name = 'MEMBER_CONFIRM'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'members' AND column_name = 'confirmed_at'
    ) THEN
        RAISE EXCEPTION 'V45: members.confirmed_at was not created';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'MEMBER_CONFIRM') THEN
        RAISE EXCEPTION 'V45: MEMBER_CONFIRM was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name = 'MEMBER_CONFIRM'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V45: SYSTEM did not receive MEMBER_CONFIRM (V30 trigger?)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
                 JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                 JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'MEMBER_CONFIRM'
    ) THEN
        RAISE EXCEPTION 'V45: ADMINISTRADOR did not receive MEMBER_CONFIRM';
    END IF;
END $$;
