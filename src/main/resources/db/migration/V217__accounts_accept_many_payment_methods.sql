SET search_path TO app, public;

-- ============================================================================
-- V217: an account accepts several payment methods.
--
-- Until now every account stored ONE payment_method_id (company accounts V206,
-- a person's own accounts V207, advisor payout accounts V184), so the same
-- bank account had to be loaded once per method (transfer, Pago Móvil, check).
-- The methods an account accepts now live in a join table per account type,
-- with real foreign keys and cascade. What an account needs is the sum of the
-- is_mandatory_* checks of its methods, validated by each service.
--
-- Existing rows keep their single method (copied to the join table). Rows are
-- not merged automatically because payment lines already point at them — except
-- a person's own accounts, which no payment references: those that are the same
-- account (same bank + number / phone / email) are folded into one.
-- ============================================================================

-- ─── 1. Join tables ─────────────────────────────────────────────────────────
CREATE TABLE company_bank_account_payment_methods
(
    company_bank_account_id BIGINT NOT NULL REFERENCES company_bank_accounts (company_bank_accounts_id) ON DELETE CASCADE,
    payment_method_id       BIGINT NOT NULL REFERENCES payment_methods (payment_methods_id),
    PRIMARY KEY (company_bank_account_id, payment_method_id)
);

CREATE TABLE payer_bank_account_payment_methods
(
    payer_bank_account_id BIGINT NOT NULL REFERENCES payer_bank_accounts (payer_bank_accounts_id) ON DELETE CASCADE,
    payment_method_id     BIGINT NOT NULL REFERENCES payment_methods (payment_methods_id),
    PRIMARY KEY (payer_bank_account_id, payment_method_id)
);

CREATE TABLE promoter_payout_account_payment_methods
(
    promoter_payout_account_id BIGINT NOT NULL REFERENCES promoter_payout_accounts (promoter_payout_accounts_id) ON DELETE CASCADE,
    payment_method_id          BIGINT NOT NULL REFERENCES payment_methods (payment_methods_id),
    PRIMARY KEY (promoter_payout_account_id, payment_method_id)
);

CREATE INDEX idx_company_account_methods_method ON company_bank_account_payment_methods (payment_method_id);
CREATE INDEX idx_payer_account_methods_method ON payer_bank_account_payment_methods (payment_method_id);
CREATE INDEX idx_payout_account_methods_method ON promoter_payout_account_payment_methods (payment_method_id);

COMMENT ON TABLE company_bank_account_payment_methods IS
    'Métodos de pago que acepta cada cuenta de la empresa; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.';
COMMENT ON TABLE payer_bank_account_payment_methods IS
    'Métodos de pago que acepta cada cuenta propia de una persona; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.';
COMMENT ON TABLE promoter_payout_account_payment_methods IS
    'Métodos de pago que acepta cada cuenta de cobro del asesor; lo que exige la cuenta es la suma de los is_mandatory_* de sus métodos.';

-- ─── 2. Copy the current single method ──────────────────────────────────────
INSERT INTO company_bank_account_payment_methods (company_bank_account_id, payment_method_id)
SELECT company_bank_accounts_id, payment_method_id FROM company_bank_accounts;

INSERT INTO payer_bank_account_payment_methods (payer_bank_account_id, payment_method_id)
SELECT payer_bank_accounts_id, payment_method_id FROM payer_bank_accounts;

INSERT INTO promoter_payout_account_payment_methods (promoter_payout_account_id, payment_method_id)
SELECT promoter_payout_accounts_id, payment_method_id FROM promoter_payout_accounts;

-- ─── 3. Fold a person's duplicate accounts (same account, different method) ─
-- Survivor = the lowest id of each active group; it takes the methods of the
-- others, which are deactivated.
WITH keyed AS (
    SELECT payer_bank_accounts_id AS id,
           MIN(payer_bank_accounts_id) OVER (
               PARTITION BY person_id, COALESCE(bank_id, 0), COALESCE(account_identifier, phone, email, '')
           ) AS survivor_id
    FROM payer_bank_accounts
    WHERE is_active
)
INSERT INTO payer_bank_account_payment_methods (payer_bank_account_id, payment_method_id)
SELECT k.survivor_id, m.payment_method_id
FROM keyed k
JOIN payer_bank_account_payment_methods m ON m.payer_bank_account_id = k.id
WHERE k.id <> k.survivor_id
ON CONFLICT DO NOTHING;

UPDATE payer_bank_accounts p
SET is_active = FALSE
WHERE p.is_active
  AND p.payer_bank_accounts_id <> (
      SELECT MIN(q.payer_bank_accounts_id) FROM payer_bank_accounts q
      WHERE q.is_active
        AND q.person_id = p.person_id
        AND COALESCE(q.bank_id, 0) = COALESCE(p.bank_id, 0)
        AND COALESCE(q.account_identifier, q.phone, q.email, '') = COALESCE(p.account_identifier, p.phone, p.email, ''));

-- ─── 4. Drop the single-method columns ──────────────────────────────────────
-- The identity of a person's account no longer includes the method.
DROP INDEX uq_payer_bank_accounts_identity;
CREATE UNIQUE INDEX uq_payer_bank_accounts_identity
    ON payer_bank_accounts (person_id, COALESCE(bank_id, 0), COALESCE(account_identifier, phone, email, ''))
    WHERE is_active;

ALTER TABLE company_bank_accounts DROP COLUMN payment_method_id;
ALTER TABLE payer_bank_accounts DROP COLUMN payment_method_id;
ALTER TABLE promoter_payout_accounts DROP COLUMN payment_method_id;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM company_bank_accounts a
               WHERE NOT EXISTS (SELECT 1 FROM company_bank_account_payment_methods m
                                 WHERE m.company_bank_account_id = a.company_bank_accounts_id))
       OR EXISTS (SELECT 1 FROM payer_bank_accounts a
                  WHERE a.is_active AND NOT EXISTS (SELECT 1 FROM payer_bank_account_payment_methods m
                                                    WHERE m.payer_bank_account_id = a.payer_bank_accounts_id))
       OR EXISTS (SELECT 1 FROM promoter_payout_accounts a
                  WHERE NOT EXISTS (SELECT 1 FROM promoter_payout_account_payment_methods m
                                    WHERE m.promoter_payout_account_id = a.promoter_payout_accounts_id)) THEN
        RAISE EXCEPTION 'V217: an account was left without a payment method';
    END IF;
END $$;
