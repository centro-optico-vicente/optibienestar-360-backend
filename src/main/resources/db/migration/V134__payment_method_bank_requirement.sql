SET search_path TO app, public;

ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS is_mandatory_bank BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE payment_methods
SET is_mandatory_bank = CASE
    WHEN code IN ('PAGO_MOVIL', 'CHECK', 'BANK_TRANSFER', 'BANK_DEPOSIT') THEN TRUE
    ELSE FALSE
END
WHERE code IS NOT NULL;
