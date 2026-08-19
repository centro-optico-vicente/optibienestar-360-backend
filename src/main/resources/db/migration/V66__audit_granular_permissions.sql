SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V66: per-domain audit/report permissions (spec 07-audit.md §Permisos
-- granulares por dominio), same shape as V54:
--   <DOMAIN>_AUDIT_VIEW      — view the change timeline of a record in that domain
--   <DOMAIN>_AUDIT_RESTORE   — restore a record to a prior point in its history
--                              (permission created, functionality NOT implemented)
--   <DOMAIN>_REPORT_GENERATE — generate a report scoped to that domain
-- plus a single, non-domain-repeated REPORT_SHARE (create a report_share link
-- — permission and table ready, functionality NOT implemented), parked under
-- the REPORTS domain since permissions.domain_id is mandatory.
--
-- Applied to the 10 domains seeded in V5 (USERS, MEMBERS, ALLIES, PLANS,
-- MEMBERSHIPS, PAYMENTS, PROMOTERS, COMMISSIONS, REFERRALS, REPORTS) — not to
-- the AUDIT domain itself (V64 already covers the audit admin surface).
--
-- SYSTEM receives every new permission automatically via the V30 trigger.
-- ADMINISTRADOR gets explicit grants for the same reason as V54/V64.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('USER_AUDIT_VIEW',          'USERS',       'Ver el historial de cambios de un usuario'),
    ('USER_AUDIT_RESTORE',       'USERS',       'Restaurar un usuario a un punto de su historial'),
    ('USER_REPORT_GENERATE',     'USERS',       'Generar reportes de usuarios'),

    ('MEMBER_AUDIT_VIEW',        'MEMBERS',     'Ver el historial de cambios de un afiliado'),
    ('MEMBER_AUDIT_RESTORE',     'MEMBERS',     'Restaurar un afiliado a un punto de su historial'),
    ('MEMBER_REPORT_GENERATE',   'MEMBERS',     'Generar reportes de afiliados'),

    ('ALLY_AUDIT_VIEW',          'ALLIES',      'Ver el historial de cambios de un aliado'),
    ('ALLY_AUDIT_RESTORE',       'ALLIES',      'Restaurar un aliado a un punto de su historial'),
    ('ALLY_REPORT_GENERATE',     'ALLIES',      'Generar reportes de aliados'),

    ('PLAN_AUDIT_VIEW',          'PLANS',       'Ver el historial de cambios de un plan'),
    ('PLAN_AUDIT_RESTORE',       'PLANS',       'Restaurar un plan a un punto de su historial'),
    ('PLAN_REPORT_GENERATE',     'PLANS',       'Generar reportes de planes'),

    ('MEMBERSHIP_AUDIT_VIEW',        'MEMBERSHIPS', 'Ver el historial de cambios de una membresía'),
    ('MEMBERSHIP_AUDIT_RESTORE',     'MEMBERSHIPS', 'Restaurar una membresía a un punto de su historial'),
    ('MEMBERSHIP_REPORT_GENERATE',   'MEMBERSHIPS', 'Generar reportes de membresías'),

    ('PAYMENT_AUDIT_VIEW',       'PAYMENTS',    'Ver el historial de cambios de un pago'),
    ('PAYMENT_AUDIT_RESTORE',    'PAYMENTS',    'Restaurar un pago a un punto de su historial'),
    ('PAYMENT_REPORT_GENERATE',  'PAYMENTS',    'Generar reportes de pagos'),

    ('PROMOTER_AUDIT_VIEW',      'PROMOTERS',   'Ver el historial de cambios de un promotor'),
    ('PROMOTER_AUDIT_RESTORE',   'PROMOTERS',   'Restaurar un promotor a un punto de su historial'),
    ('PROMOTER_REPORT_GENERATE', 'PROMOTERS',   'Generar reportes de promotores'),

    ('COMMISSION_AUDIT_VIEW',        'COMMISSIONS', 'Ver el historial de cambios de una comisión'),
    ('COMMISSION_AUDIT_RESTORE',     'COMMISSIONS', 'Restaurar una comisión a un punto de su historial'),
    ('COMMISSION_REPORT_GENERATE',   'COMMISSIONS', 'Generar reportes de comisiones'),

    ('REFERRAL_AUDIT_VIEW',      'REFERRALS',   'Ver el historial de cambios de un referido'),
    ('REFERRAL_AUDIT_RESTORE',   'REFERRALS',   'Restaurar un referido a un punto de su historial'),
    ('REFERRAL_REPORT_GENERATE', 'REFERRALS',   'Generar reportes de referidos'),

    ('REPORT_AUDIT_VIEW',        'REPORTS',     'Ver el historial de cambios del panel de reportes'),
    ('REPORT_AUDIT_RESTORE',     'REPORTS',     'Restaurar una configuración de reportes a un punto de su historial'),
    ('REPORT_REPORT_GENERATE',   'REPORTS',     'Generar reportes del panel de reportes'),

    ('REPORT_SHARE',             'REPORTS',     'Crear un enlace para compartir un reporte generado')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: full access to all new audit/report permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN (
      'USER_AUDIT_VIEW', 'USER_AUDIT_RESTORE', 'USER_REPORT_GENERATE',
      'MEMBER_AUDIT_VIEW', 'MEMBER_AUDIT_RESTORE', 'MEMBER_REPORT_GENERATE',
      'ALLY_AUDIT_VIEW', 'ALLY_AUDIT_RESTORE', 'ALLY_REPORT_GENERATE',
      'PLAN_AUDIT_VIEW', 'PLAN_AUDIT_RESTORE', 'PLAN_REPORT_GENERATE',
      'MEMBERSHIP_AUDIT_VIEW', 'MEMBERSHIP_AUDIT_RESTORE', 'MEMBERSHIP_REPORT_GENERATE',
      'PAYMENT_AUDIT_VIEW', 'PAYMENT_AUDIT_RESTORE', 'PAYMENT_REPORT_GENERATE',
      'PROMOTER_AUDIT_VIEW', 'PROMOTER_AUDIT_RESTORE', 'PROMOTER_REPORT_GENERATE',
      'COMMISSION_AUDIT_VIEW', 'COMMISSION_AUDIT_RESTORE', 'COMMISSION_REPORT_GENERATE',
      'REFERRAL_AUDIT_VIEW', 'REFERRAL_AUDIT_RESTORE', 'REFERRAL_REPORT_GENERATE',
      'REPORT_AUDIT_VIEW', 'REPORT_AUDIT_RESTORE', 'REPORT_REPORT_GENERATE',
      'REPORT_SHARE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    new_permission TEXT;
BEGIN
    FOREACH new_permission IN ARRAY ARRAY[
        'USER_AUDIT_VIEW', 'USER_AUDIT_RESTORE', 'USER_REPORT_GENERATE',
        'MEMBER_AUDIT_VIEW', 'MEMBER_AUDIT_RESTORE', 'MEMBER_REPORT_GENERATE',
        'ALLY_AUDIT_VIEW', 'ALLY_AUDIT_RESTORE', 'ALLY_REPORT_GENERATE',
        'PLAN_AUDIT_VIEW', 'PLAN_AUDIT_RESTORE', 'PLAN_REPORT_GENERATE',
        'MEMBERSHIP_AUDIT_VIEW', 'MEMBERSHIP_AUDIT_RESTORE', 'MEMBERSHIP_REPORT_GENERATE',
        'PAYMENT_AUDIT_VIEW', 'PAYMENT_AUDIT_RESTORE', 'PAYMENT_REPORT_GENERATE',
        'PROMOTER_AUDIT_VIEW', 'PROMOTER_AUDIT_RESTORE', 'PROMOTER_REPORT_GENERATE',
        'COMMISSION_AUDIT_VIEW', 'COMMISSION_AUDIT_RESTORE', 'COMMISSION_REPORT_GENERATE',
        'REFERRAL_AUDIT_VIEW', 'REFERRAL_AUDIT_RESTORE', 'REFERRAL_REPORT_GENERATE',
        'REPORT_AUDIT_VIEW', 'REPORT_AUDIT_RESTORE', 'REPORT_REPORT_GENERATE',
        'REPORT_SHARE'
    ]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
            RAISE EXCEPTION 'V66: % was not created', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V66: SYSTEM did not receive % (V30 trigger?)', new_permission;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
                     JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
        ) THEN
            RAISE EXCEPTION 'V66: ADMINISTRADOR did not receive %', new_permission;
        END IF;
    END LOOP;
END $$;
