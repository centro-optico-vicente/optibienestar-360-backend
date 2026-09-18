SET search_path TO app, public;

-- ============================================================================
-- V121: closes gaps in the payments CRUD surface flagged by the user testing
-- the "Finanzas" screens (hub plan ".ai/plans/2026-09-17-payments-unification-plan.md"):
--
--   1. Admin `PAYMENT_DELETE` never existed — AdminPaymentController had no
--      DELETE endpoint at all, even though the frontend already shipped a
--      delete button (only works once #2 below lands the endpoint).
--   2. Self-service (AFILIADO): /v1/me/payments was read-only. AFILIADO
--      already holds PAYMENT_CREATE (renamed from PAYMENT_REGISTER, V79) —
--      that permission gates the ADMIN registration endpoint though, not a
--      member registering their OWN payment, so a distinct _OWN permission
--      is needed instead of reusing it for the new self-service endpoint.
--   3. Self-service (PROMOTOR): /v1/promoter/me/payments (direction=IN) was
--      read-only. A commercial promoter manages collections on behalf of
--      their downline's affiliates, so they get their own create/delete
--      permissions scoped to "their portfolio" (_DOWNLINE), mirroring the
--      ownedMember() check PromoterCollectionService already applies for
--      reminders/payment-promises.
--
-- Approve/reject for the promoter surface (PAYMENT_APPROVE_DOWNLINE /
-- PAYMENT_REJECT_DOWNLINE) are created here as GRANULAR permissions but
-- deliberately NOT granted to PROMOTOR by default — today only staff with
-- bank access does the manual review, per explicit decision in the hub plan
-- follow-up conversation. They exist so an admin can grant them later via
-- the role/permission screen without another migration, once bank
-- integration or a specific ops policy calls for it.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('PAYMENT_DELETE',            'PAYMENTS', 'Eliminar un pago pendiente'),
    ('PAYMENT_CREATE_OWN',        'PAYMENTS', 'Registrar un pago propio para revisión'),
    ('PAYMENT_DELETE_OWN',        'PAYMENTS', 'Eliminar un pago propio pendiente'),
    ('PAYMENT_CREATE_DOWNLINE',   'PAYMENTS', 'Registrar un cobro para un afiliado de la cartera del promotor'),
    ('PAYMENT_APPROVE_DOWNLINE',  'PAYMENTS', 'Aprobar un cobro pendiente de la cartera del promotor'),
    ('PAYMENT_REJECT_DOWNLINE',   'PAYMENTS', 'Rechazar un cobro pendiente de la cartera del promotor'),
    ('PAYMENT_DELETE_DOWNLINE',   'PAYMENTS', 'Eliminar un cobro pendiente de la cartera del promotor')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- SYSTEM / ADMINISTRADOR / OPERADOR / OPERADOR_MEDICO: PAYMENT_DELETE joins
-- the rest of the review workflow (PAYMENT_APPROVE/PAYMENT_REJECT) these
-- roles already hold. The _OWN/_DOWNLINE ones are scoped to AFILIADO/PROMOTOR
-- below — these business-ops roles already operate at full ALL scope.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR', 'OPERADOR_MEDICO')
  AND p.name = 'PAYMENT_DELETE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- AFILIADO: full self-service over their own payments (register + delete
-- while pending); approve/reject stays exclusively an admin action.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'AFILIADO'
  AND p.name IN ('PAYMENT_CREATE_OWN', 'PAYMENT_DELETE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- PROMOTOR: register + delete-while-pending a downline collection. NOT
-- approve/reject — see header comment.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'PROMOTOR'
  AND p.name IN ('PAYMENT_CREATE_DOWNLINE', 'PAYMENT_DELETE_DOWNLINE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('PAYMENT_DELETE'), ('PAYMENT_CREATE_OWN'), ('PAYMENT_DELETE_OWN'),
            ('PAYMENT_CREATE_DOWNLINE'), ('PAYMENT_APPROVE_DOWNLINE'),
            ('PAYMENT_REJECT_DOWNLINE'), ('PAYMENT_DELETE_DOWNLINE')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V121: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name = 'PAYMENT_DELETE'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V121: SYSTEM did not receive PAYMENT_DELETE';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('PAYMENT_CREATE_OWN', 'PAYMENT_DELETE_OWN')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'AFILIADO'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V121: AFILIADO did not receive its new self-service permissions';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('PAYMENT_CREATE_DOWNLINE', 'PAYMENT_DELETE_DOWNLINE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'PROMOTOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V121: PROMOTOR did not receive its new downline permissions';
    END IF;
END $$;
