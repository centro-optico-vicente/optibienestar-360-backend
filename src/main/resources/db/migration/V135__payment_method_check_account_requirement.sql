SET search_path TO app, public;

-- V130 only marked BANK_TRANSFER/BANK_DEPOSIT as requiring an account
-- type/number. Business rule: a check also requires a bank account (the
-- account the check is drawn against) — V134 already marked CHECK as
-- requiring a bank; this closes the gap for the account fields so
-- cheque/transferencia both demand bank + account, while pago móvil keeps
-- requiring only the bank (no account number).
UPDATE payment_methods
SET is_mandatory_account_type = TRUE,
    is_mandatory_account_code = TRUE
WHERE code = 'CHECK';
