SET search_path TO app, public;

-- OUT payments are drafted before an operator submits them for review.
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments
    ADD CONSTRAINT payments_status_check
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE payment_lines DROP CONSTRAINT IF EXISTS payment_lines_status_check;
ALTER TABLE payment_lines ADD CONSTRAINT payment_lines_status_check
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE payment_lines DROP CONSTRAINT IF EXISTS chk_payment_lines_review_consistency;
ALTER TABLE payment_lines ADD CONSTRAINT chk_payment_lines_review_consistency CHECK (
    (status IN ('DRAFT', 'PENDING') AND reviewed_at IS NULL AND reviewed_by IS NULL)
    OR (status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
);

ALTER TABLE payment_methods ADD COLUMN IF NOT EXISTS description VARCHAR(255);
ALTER TABLE payment_lines ADD COLUMN IF NOT EXISTS identification VARCHAR(80);
ALTER TABLE payment_lines ADD COLUMN IF NOT EXISTS bank_account_type VARCHAR(40);
ALTER TABLE payment_lines ADD COLUMN IF NOT EXISTS bank_account_code VARCHAR(40);
ALTER TABLE payment_lines ADD COLUMN IF NOT EXISTS bank_account_identifier VARCHAR(120);
ALTER TABLE payment_lines ADD COLUMN IF NOT EXISTS phone VARCHAR(40);
ALTER TABLE payment_lines ADD COLUMN IF NOT EXISTS email VARCHAR(160);

UPDATE payment_methods
SET description = v.description
FROM (VALUES
 ('BANK_TRANSFER','Transferencia bancaria local'),
 ('CASH','Efectivo'),
 ('ZELLE','Transferencia Zelle'),
 ('PAGO_MOVIL','Pago móvil'),
 ('CRYPTO','Criptoactivo'),
 ('INTERNATIONAL_TRANSFER','Transferencia internacional'),
 ('OTHER','Otro método de pago')
) v(code, description)
WHERE payment_methods.code = v.code AND payment_methods.description IS NULL;

-- Collections had historically been exposed as PAYMENT_*; keep the permission
-- rows and grants intact while making direction explicit in the names.
DO $$
DECLARE
    old_name text;
    new_name text;
BEGIN
    FOREACH old_name IN ARRAY ARRAY[
        'PAYMENT_VIEW_ALL','PAYMENT_VIEW_OWN','PAYMENT_CREATE','PAYMENT_APPROVE','PAYMENT_REJECT',
        'PAYMENT_DELETE','PAYMENT_CREATE_OWN','PAYMENT_DELETE_OWN',
        'PAYMENT_CREATE_DOWNLINE','PAYMENT_APPROVE_DOWNLINE',
        'PAYMENT_REJECT_DOWNLINE','PAYMENT_DELETE_DOWNLINE'
    ] LOOP
        new_name := replace(old_name, 'PAYMENT_', 'COLLECTION_');
        UPDATE permissions SET name = new_name,
            description = replace(description, 'pago', 'cobro')
        WHERE name = old_name
          AND NOT EXISTS (SELECT 1 FROM permissions p2 WHERE p2.name = new_name);
    END LOOP;
END $$;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, d.permission_domains_id, v.description
FROM (VALUES
 ('PAYMENT_VIEW_ALL','Ver pagos de salida'),
 ('PAYMENT_CREATE','Crear pagos de salida en borrador'),
 ('PAYMENT_UPDATE','Editar pagos de salida en borrador'),
 ('PAYMENT_DELETE','Eliminar pagos de salida en borrador'),
 ('PAYMENT_PROCESS','Enviar un pago de salida a revisión'),
 ('PAYMENT_APPROVE','Aprobar un pago de salida pendiente'),
 ('PAYMENT_REJECT','Rechazar un pago de salida pendiente')
) v(name, description)
JOIN permission_domains d ON d.code = 'PAYMENTS'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM','ADMINISTRADOR','OPERADOR')
  AND p.name IN ('PAYMENT_VIEW_ALL','PAYMENT_CREATE','PAYMENT_UPDATE','PAYMENT_DELETE',
                 'PAYMENT_PROCESS','PAYMENT_APPROVE','PAYMENT_REJECT')
ON CONFLICT (role_id, permission_id) DO NOTHING;
