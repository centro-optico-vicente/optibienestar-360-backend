SET search_path TO app, public;

-- ============================================================================
-- V201: payments without a membership, per-person credit balance and the
-- company's own bank accounts (hub plan ".ai/plans/2026-10-29-payments-without-affiliation.md").
--
--   1. V120 forced every IN payment to carry a membership_id. A person must be
--      able to pay the company (advance, partial payment, family member paying
--      for the holder) WITHOUT one, so the CHECK now only pins OUT payments to
--      NULL; IN may or may not have a membership.
--   2. person_credit_entries: append-only ledger of the "saldo a favor" per
--      person and currency. An approved IN payment with no membership adds a
--      CREDIT; assigning it to a membership later adds a negative APPLIED.
--      Balance = SUM(amount) per (person, currency).
--   3. company_bank_accounts: the accounts OptiBienestar receives money in;
--      the payer picks one on each payment line to say where they paid.
--   4. Permissions (granular, one per action) + portal self-service grants.
-- ============================================================================

-- ─── 1. IN payments may have no membership ──────────────────────────────────
ALTER TABLE payments DROP CONSTRAINT chk_payments_membership_by_direction;
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_membership_by_direction CHECK (
        direction = 'IN'
        OR (direction = 'OUT' AND membership_id IS NULL)
    );

-- ─── 2. Per-person credit ledger ────────────────────────────────────────────
CREATE TABLE person_credit_entries
(
    person_credit_entries_id BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    person_id                BIGINT         NOT NULL REFERENCES persons (persons_id),
    payment_id               BIGINT         NOT NULL REFERENCES payments (payments_id),
    membership_id            BIGINT         REFERENCES memberships (memberships_id),
    currency_id              BIGINT         NOT NULL REFERENCES currencies (currencies_id),
    entry_type               VARCHAR(20)    NOT NULL,
    amount                   NUMERIC(12, 2) NOT NULL,
    note                     VARCHAR(255),

    -- Audit + soft-delete
    is_active                BOOLEAN        NOT NULL DEFAULT TRUE,
    status                   VARCHAR(50),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT chk_person_credit_entries_type CHECK (entry_type IN ('CREDIT', 'APPLIED', 'REVERSED')),
    CONSTRAINT chk_person_credit_entries_sign CHECK (
        (entry_type = 'CREDIT' AND amount > 0) OR (entry_type <> 'CREDIT' AND amount < 0))
);

CREATE INDEX idx_person_credit_entries_person ON person_credit_entries (person_id, currency_id) WHERE is_active;
CREATE INDEX idx_person_credit_entries_payment ON person_credit_entries (payment_id);

CREATE TRIGGER trg_person_credit_entries_updated_at
    BEFORE UPDATE ON person_credit_entries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE person_credit_entries IS
    'Libro de saldo a favor por persona y moneda: CREDIT (pago aprobado sin membresía) / APPLIED (asignado a una membresía), importe con signo.';

-- ─── 3. Company bank accounts ───────────────────────────────────────────────
CREATE TABLE company_bank_accounts
(
    company_bank_accounts_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    bank_id                  BIGINT       NOT NULL REFERENCES banks (banks_id),
    currency_id              BIGINT       NOT NULL REFERENCES currencies (currencies_id),
    account_type             VARCHAR(40)  NOT NULL,
    account_number           VARCHAR(40)  NOT NULL,
    holder_name              VARCHAR(160) NOT NULL,
    holder_document          VARCHAR(30)  NOT NULL,
    notes                    VARCHAR(255),
    sort_order               INT          NOT NULL DEFAULT 0,

    -- Audit + soft-delete
    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    status                   VARCHAR(50),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID
);

CREATE UNIQUE INDEX uq_company_bank_accounts_number
    ON company_bank_accounts (bank_id, account_number) WHERE is_active;

CREATE TRIGGER trg_company_bank_accounts_updated_at
    BEFORE UPDATE ON company_bank_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE company_bank_accounts IS
    'Cuentas bancarias propias de OptiBienestar donde los afiliados depositan; se elige una por línea de pago.';

ALTER TABLE payment_lines ADD COLUMN company_bank_account_id BIGINT REFERENCES company_bank_accounts (company_bank_accounts_id);
COMMENT ON COLUMN payment_lines.company_bank_account_id IS
    'Cuenta de la empresa en la que el pagador indica haber pagado (opcional).';

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('company_bank_account', 'Cuentas bancarias de la empresa', 'company_bank_accounts')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── 4. Permissions ─────────────────────────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COMPANY_BANK_ACCOUNT_VIEW',   'PAYMENT_CATALOG', 'Ver las cuentas bancarias de la empresa'),
    ('COMPANY_BANK_ACCOUNT_CREATE', 'PAYMENT_CATALOG', 'Crear cuentas bancarias de la empresa'),
    ('COMPANY_BANK_ACCOUNT_UPDATE', 'PAYMENT_CATALOG', 'Editar cuentas bancarias de la empresa'),
    ('COMPANY_BANK_ACCOUNT_DELETE', 'PAYMENT_CATALOG', 'Eliminar cuentas bancarias de la empresa'),
    ('COLLECTION_ASSIGN_MEMBERSHIP', 'PAYMENTS',       'Asignar un cobro o saldo a favor a una membresía'),
    ('MEMBER_ADVISOR_VIEW_OWN',     'MEMBERS',         'Ver el propio asesor y su historial')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO permissions (name, domain_id, description)
SELECT 'PROMOTER_PORTFOLIO_CREATE', p.domain_id, 'Registrar una persona nueva en la propia cartera'
FROM permissions p WHERE p.name = 'PROMOTER_VIEW_OWN';

-- Business-ops roles administer the company accounts and assign credit
-- (SYSTEM gets everything through the V30 trigger).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('COMPANY_BANK_ACCOUNT_VIEW', 'COMPANY_BANK_ACCOUNT_CREATE',
                 'COMPANY_BANK_ACCOUNT_UPDATE', 'COMPANY_BANK_ACCOUNT_DELETE',
                 'COLLECTION_ASSIGN_MEMBERSHIP')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Every portal role is also a person who may pay and see their advisor:
-- self-service payments (own) for ASESOR and ALIADO, advisor view for all three.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('PROMOTOR', 'ALIADO')
  AND p.name IN ('COLLECTION_VIEW_OWN', 'COLLECTION_CREATE_OWN', 'COLLECTION_DELETE_OWN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('AFILIADO', 'PROMOTOR', 'ALIADO') AND p.name = 'MEMBER_ADVISOR_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name = 'PROMOTOR' AND p.name = 'PROMOTER_PORTFOLIO_CREATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('COMPANY_BANK_ACCOUNT_VIEW'), ('COMPANY_BANK_ACCOUNT_CREATE'),
            ('COMPANY_BANK_ACCOUNT_UPDATE'), ('COMPANY_BANK_ACCOUNT_DELETE'),
            ('COLLECTION_ASSIGN_MEMBERSHIP'), ('MEMBER_ADVISOR_VIEW_OWN'),
            ('PROMOTER_PORTFOLIO_CREATE')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V201: one or more new permissions were not created';
    END IF;
END $$;
