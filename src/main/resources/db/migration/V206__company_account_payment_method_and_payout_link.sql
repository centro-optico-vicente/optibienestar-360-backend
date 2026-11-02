SET search_path TO app, public;

-- ============================================================================
-- V206: company bank accounts get a payment METHOD, like the advisor payout
-- accounts (promoter_payout_accounts). Which fields an account needs now comes
-- from the method's is_mandatory_* flags instead of being fixed: a Pago Móvil
-- account needs bank, holder document and phone, but no account type or number;
-- a bank transfer account needs account type and number, but no phone; Zelle
-- needs an email. Existing rows were all bank accounts, so they become
-- BANK_TRANSFER.
--
-- It also links a payment line to the advisor payout account it was paid to, so
-- a commission payout (direction OUT) records both ends: the company account the
-- money left from (company_bank_account_id) and the advisor account it went to.
-- ============================================================================

ALTER TABLE company_bank_accounts
    ADD COLUMN payment_method_id BIGINT REFERENCES payment_methods (payment_methods_id),
    ADD COLUMN phone             VARCHAR(30),
    ADD COLUMN email             VARCHAR(160);

UPDATE company_bank_accounts
SET payment_method_id = (SELECT payment_methods_id FROM payment_methods WHERE code = 'BANK_TRANSFER');

ALTER TABLE company_bank_accounts ALTER COLUMN payment_method_id SET NOT NULL;

-- Required or not is decided per method by the service.
ALTER TABLE company_bank_accounts
    ALTER COLUMN bank_id DROP NOT NULL,
    ALTER COLUMN account_type DROP NOT NULL,
    ALTER COLUMN account_number DROP NOT NULL,
    ALTER COLUMN holder_document DROP NOT NULL;

COMMENT ON COLUMN company_bank_accounts.payment_method_id IS
    'Método de pago de la cuenta (pago móvil, transferencia, Zelle…); sus indicadores is_mandatory_* deciden qué campos exige.';
COMMENT ON COLUMN company_bank_accounts.phone IS 'Teléfono de la cuenta (pago móvil).';
COMMENT ON COLUMN company_bank_accounts.email IS 'Correo de la cuenta (Zelle y similares).';

ALTER TABLE payment_lines
    ADD COLUMN payout_account_id BIGINT REFERENCES promoter_payout_accounts (promoter_payout_accounts_id);

COMMENT ON COLUMN payment_lines.payout_account_id IS
    'Cuenta de cobro del asesor a la que se pagó (pagos de salida); la cuenta de origen es company_bank_account_id.';
