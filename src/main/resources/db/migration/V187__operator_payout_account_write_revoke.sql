SET search_path TO app, public;

-- ============================================================================
-- V187: OPERADOR deja de gestionar por defecto las cuentas de cobro de los
-- asesores.
--
-- Decisión del usuario (2026-10-21): el rol OPERADOR solo registra, edita o
-- elimina cuentas de cobro de asesores si alguien se lo asigna explícitamente
-- desde la pantalla de Roles (permisos granulares, uno por acción). Las
-- cuentas de cobro son datos financieros sensibles; los permisos de escritura
-- no se entregan por defecto a OPERADOR.
--
-- V184 le había dado a OPERADOR VIEW_ALL + CREATE + UPDATE + DELETE. Aquí se
-- le quitan CREATE / UPDATE / DELETE y se conserva
-- PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL: OPERADOR sigue registrando pagos de salida
-- (V129/V132) y necesita ver la cuenta destino para prellenar el pago.
--
-- SYSTEM y ADMINISTRADOR no cambian. Si un ambiente ya le reasignó estos
-- permisos a OPERADOR a mano, esta migración también los quita: a partir de
-- aquí la asignación se hace de nuevo desde Roles.
-- ============================================================================

DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.roles_id
  AND rp.permission_id = p.permissions_id
  AND r.name = 'OPERADOR'
  AND p.name IN ('PROMOTER_PAYOUT_ACCOUNT_CREATE',
                 'PROMOTER_PAYOUT_ACCOUNT_UPDATE',
                 'PROMOTER_PAYOUT_ACCOUNT_DELETE');

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'OPERADOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id
        WHERE p.name IN ('PROMOTER_PAYOUT_ACCOUNT_CREATE',
                         'PROMOTER_PAYOUT_ACCOUNT_UPDATE',
                         'PROMOTER_PAYOUT_ACCOUNT_DELETE')
    ) THEN
        RAISE EXCEPTION 'V187: OPERADOR still holds payout account write permissions';
    END IF;

    IF EXISTS (SELECT 1 FROM roles WHERE name = 'OPERADOR') AND NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'OPERADOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id
                          AND p.name = 'PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL'
    ) THEN
        RAISE EXCEPTION 'V187: OPERADOR lost PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL (needed to pay advisors)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES ('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL'), ('PROMOTER_PAYOUT_ACCOUNT_CREATE'),
                     ('PROMOTER_PAYOUT_ACCOUNT_UPDATE'), ('PROMOTER_PAYOUT_ACCOUNT_DELETE')) AS want_perm(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = want_perm.name
        )
    ) THEN
        RAISE EXCEPTION 'V187: ADMINISTRADOR must keep every payout account permission';
    END IF;
END $$;
