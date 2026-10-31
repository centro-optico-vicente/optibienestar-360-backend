SET search_path TO app, public;

-- ============================================================================
-- V206: a Pago Móvil company account is identified by a phone number, not only by
-- the account number. The phone is what the payer copies (phone, document, bank
-- code + name, amount) into their bank app. Required by the service when the
-- account type is PAGO_MOVIL; nullable for every other type.
-- ============================================================================

ALTER TABLE company_bank_accounts ADD COLUMN mobile_payment_phone VARCHAR(30);

COMMENT ON COLUMN company_bank_accounts.mobile_payment_phone IS
    'Teléfono de Pago Móvil de la cuenta; obligatorio cuando account_type = PAGO_MOVIL.';
