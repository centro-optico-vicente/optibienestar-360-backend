SET search_path TO app, public;

-- ============================================================================
-- V204: (1) a person can remember the bank account they pay FROM, so the next
-- payment does not need every origin field again; (2) a company bank account can
-- be flagged public and exposed through an unauthenticated endpoint.
-- Hub plan ".ai/plans/2026-10-29-payments-without-affiliation.md".
-- ============================================================================

CREATE TABLE person_bank_accounts
(
    person_bank_accounts_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    person_id               BIGINT       NOT NULL REFERENCES persons (persons_id),
    bank_id                 BIGINT       NOT NULL REFERENCES banks (banks_id),
    account_type            VARCHAR(40),
    account_code            VARCHAR(40),
    account_identifier      VARCHAR(120) NOT NULL,
    identification          VARCHAR(80),
    alias                   VARCHAR(80),
    last_used_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Audit + soft-delete
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    status                  VARCHAR(50),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID
);

CREATE UNIQUE INDEX uq_person_bank_accounts_identity
    ON person_bank_accounts (person_id, bank_id, account_identifier) WHERE is_active;
CREATE INDEX idx_person_bank_accounts_person ON person_bank_accounts (person_id, last_used_at DESC) WHERE is_active;

CREATE TRIGGER trg_person_bank_accounts_updated_at
    BEFORE UPDATE ON person_bank_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE person_bank_accounts IS
    'Cuentas de origen que una persona decidió recordar para no reescribirlas en el siguiente pago.';

ALTER TABLE company_bank_accounts ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN company_bank_accounts.is_public IS
    'TRUE = visible sin autenticación en GET /v1/public/company-bank-accounts; FALSE = solo para quien registra pagos.';
