SET search_path TO app, public;

-- ============================================================================
-- V207: a person's own accounts (payer_bank_accounts) get a payment method, like
-- the company accounts (V206) and the advisor payout accounts. The method's
-- is_mandatory_* flags decide what each account needs, so a Pago Móvil account
-- (bank, holder document, phone) can be remembered and, later, used for refunds;
-- until now only accounts with an account number fit. Existing rows all had an
-- account number, so they become BANK_TRANSFER.
-- ============================================================================

ALTER TABLE payer_bank_accounts
    ADD COLUMN payment_method_id BIGINT REFERENCES payment_methods (payment_methods_id),
    ADD COLUMN phone             VARCHAR(30),
    ADD COLUMN email             VARCHAR(160);

UPDATE payer_bank_accounts
SET payment_method_id = (SELECT payment_methods_id FROM payment_methods WHERE code = 'BANK_TRANSFER');

ALTER TABLE payer_bank_accounts ALTER COLUMN payment_method_id SET NOT NULL;

-- Required or not is decided per method by the service.
ALTER TABLE payer_bank_accounts
    ALTER COLUMN bank_id DROP NOT NULL,
    ALTER COLUMN account_identifier DROP NOT NULL;

-- The same account is the same method + bank + (account number | phone | email).
DROP INDEX uq_payer_bank_accounts_identity;
CREATE UNIQUE INDEX uq_payer_bank_accounts_identity
    ON payer_bank_accounts (person_id, payment_method_id, COALESCE(bank_id, 0),
                            COALESCE(account_identifier, phone, email, ''))
    WHERE is_active;

COMMENT ON COLUMN payer_bank_accounts.payment_method_id IS
    'Método de pago de la cuenta; sus indicadores is_mandatory_* deciden qué campos exige.';
COMMENT ON COLUMN payer_bank_accounts.phone IS 'Teléfono de la cuenta (pago móvil).';
COMMENT ON COLUMN payer_bank_accounts.email IS 'Correo de la cuenta (Zelle y similares).';
