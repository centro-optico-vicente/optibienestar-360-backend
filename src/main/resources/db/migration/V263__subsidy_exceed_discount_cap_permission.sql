SET search_path TO app, public;

-- ============================================================================
-- V263: SUBSIDY_EXCEED_DISCOUNT_CAP (hub ADR 0041 §5). An advisor (promoter)
-- creating or editing a subsidy is bound by their discount-authority cap
-- (DiscountAuthority: the most restrictive of rank and type max_discount_pct,
-- 0 % when neither is set). This permission lets them go past it, up to 100 %,
-- after an explicit acknowledgement; the subsidy audit log records the cap.
--
-- Granted to SYSTEM + ADMINISTRADOR only, like the rest of the subsidy
-- permissions were seeded (V41). It is NOT copied to every role holding
-- SUBSIDY_CREATE: that permission may have been granted to advisor roles,
-- and copying the bypass to them would void the cap it is meant to protect.
-- Admins hand it to specific roles from the roles screen.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('SUBSIDY_EXCEED_DISCOUNT_CAP', 'SUBSIDIES',
     'Otorgar subsidios por encima del tope de descuento del asesor (hasta 100 %), con confirmación')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name = 'SUBSIDY_EXCEED_DISCOUNT_CAP'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'SUBSIDY_EXCEED_DISCOUNT_CAP') THEN
        RAISE EXCEPTION 'V263: SUBSIDY_EXCEED_DISCOUNT_CAP was not created (SUBSIDIES domain missing?)';
    END IF;
END $$;
