SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V202: el afiliado registra sus propios beneficiarios.
--
-- POST /v1/me/family reutiliza las reglas del alta por el admin (tope del
-- plan, cupo BASE/EXTRA de V178 y cobro de inscripción extra), aplicadas a la
-- membresía del propio afiliado. Permiso propio para poder retirarlo sin
-- tocar MEMBER_VIEW_OWN.
-- ────────────────────────────────────────────────────────────────────────────

-- SYSTEM lo recibe por el trigger de V30.
INSERT INTO permissions (name, domain_id, description)
SELECT 'MEMBER_BENEFICIARY_CREATE_OWN', pd.permission_domains_id, 'Registrar beneficiarios en la membresía propia'
FROM permission_domains pd
WHERE pd.code = 'MEMBERS';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'AFILIADO'
  AND p.name = 'MEMBER_BENEFICIARY_CREATE_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'MEMBER_BENEFICIARY_CREATE_OWN') THEN
        RAISE EXCEPTION 'V202: MEMBER_BENEFICIARY_CREATE_OWN was not created';
    END IF;
END $$;
