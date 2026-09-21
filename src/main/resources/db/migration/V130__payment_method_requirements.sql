SET search_path TO app, public;

ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS is_mandatory_identification BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_mandatory_account_type BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_mandatory_account_code BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE payment_methods
SET is_mandatory_identification = CASE WHEN code IN ('PAGO_MOVIL', 'BANK_TRANSFER', 'INTERNATIONAL_TRANSFER', 'CHECK', 'BANK_DEPOSIT', 'DEBIT_CARD', 'CREDIT_CARD') THEN TRUE ELSE FALSE END,
    is_mandatory_account_type = CASE WHEN code IN ('BANK_TRANSFER', 'BANK_DEPOSIT') THEN TRUE ELSE FALSE END,
    is_mandatory_account_code = CASE WHEN code IN ('BANK_TRANSFER', 'BANK_DEPOSIT') THEN TRUE ELSE FALSE END
WHERE code IS NOT NULL;

UPDATE payment_methods SET description = COALESCE(description, 'Método de pago registrado en el catálogo.')
WHERE description IS NULL;
