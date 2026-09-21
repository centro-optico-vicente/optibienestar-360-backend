SET search_path TO app, public;

-- V115 introduced the generic `is_mandatory_bank_account` flag (frontend
-- field `bankAccountIdentifier`, label "Identificador de cuenta") and set it
-- TRUE for BANK_TRANSFER, INTERNATIONAL_TRANSFER, CHECK and BANK_DEPOSIT.
-- Later, V130 added the structured `is_mandatory_account_type` +
-- `is_mandatory_account_code` flags (`bankAccountType`/"Tipo de cuenta" +
-- `bankAccountCode`/"Código de cuenta") for BANK_TRANSFER and BANK_DEPOSIT,
-- and V135 extended the structured flags to CHECK.
--
-- Net effect before this migration: for BANK_TRANSFER, BANK_DEPOSIT and
-- CHECK the operator was asked for BOTH the generic account identifier AND
-- the structured type+code pair — two ways to capture the same account
-- number. That's redundant UI/data entry, not two distinct pieces of
-- information.
--
-- Fix: the structured type+code pair supersedes the generic identifier for
-- any method that already has structured flags. The generic
-- `is_mandatory_bank_account` stays mandatory ONLY for
-- INTERNATIONAL_TRANSFER, which has no type+code breakdown — it only ever
-- had one free-form identifier (e.g. IBAN / international account number),
-- so there is nothing to supersede it there.
UPDATE payment_methods
SET is_mandatory_bank_account = FALSE
WHERE code IN ('BANK_TRANSFER', 'BANK_DEPOSIT', 'CHECK');

-- INTERNATIONAL_TRANSFER is intentionally left untouched (already TRUE).
