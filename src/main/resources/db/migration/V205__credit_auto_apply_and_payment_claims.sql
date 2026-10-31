SET search_path TO app, public;

-- ============================================================================
-- V205: automatic application of a person's credit to membership charges, and
-- claims on a payment (hub plan ".ai/plans/2026-10-29-payments-without-affiliation.md",
-- ADR 0029).
--
--   1. person_credit_entries records WHICH charge an APPLIED row covered and the
--      conversion snapshot (converted amount + currency, rate used, rate date),
--      the same pattern as ADR 0015 section 5.
--   2. persons carries a staff-controlled hold on the whole credit: while
--      credit_auto_apply is FALSE nothing of the person's credit is used
--      automatically.
--   3. payment_credit_claims: the payer (or staff) flags an approved payment's
--      remaining credit as wrong / overpaid / refund requested, with an amount.
--      While a claim is OPEN that amount is frozen for automatic application.
--   4. Permissions (granular) and the daily CREDIT_AUTO_APPLY job.
-- ============================================================================

-- ─── 1. Ledger: charge link + conversion snapshot ───────────────────────────
ALTER TABLE person_credit_entries
    ADD COLUMN charge_id              BIGINT REFERENCES membership_charges (membership_charges_id),
    ADD COLUMN converted_amount       NUMERIC(12, 2),
    ADD COLUMN converted_currency_id  BIGINT REFERENCES currencies (currencies_id),
    ADD COLUMN exchange_rate_used     NUMERIC(18, 8),
    ADD COLUMN exchange_rate_date     DATE;

CREATE INDEX idx_person_credit_entries_charge ON person_credit_entries (charge_id) WHERE charge_id IS NOT NULL;

-- ─── 2. Hold on the whole credit of a person ────────────────────────────────
ALTER TABLE persons
    ADD COLUMN credit_auto_apply  BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN credit_hold_reason VARCHAR(255),
    ADD COLUMN credit_hold_by     UUID,
    ADD COLUMN credit_hold_at     TIMESTAMPTZ;

COMMENT ON COLUMN persons.credit_auto_apply IS
    'FALSE = el saldo a favor de la persona no se aplica automáticamente (reclamo, reembolso en curso); lo controla el personal.';

-- ─── 3. Claims on a payment ─────────────────────────────────────────────────
CREATE TABLE payment_credit_claims
(
    payment_credit_claims_id BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    payment_id               BIGINT         NOT NULL REFERENCES payments (payments_id),
    reason                   VARCHAR(20)    NOT NULL,
    claimed_amount           NUMERIC(12, 2) NOT NULL,
    comment                  VARCHAR(500),
    claim_status             VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    opened_by                BIGINT         REFERENCES users (users_id),
    opened_at                TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    resolved_by              BIGINT         REFERENCES users (users_id),
    resolved_at              TIMESTAMPTZ,
    resolution_note          VARCHAR(500),
    refunded_amount          NUMERIC(12, 2),

    -- Audit + soft-delete
    is_active                BOOLEAN        NOT NULL DEFAULT TRUE,
    status                   VARCHAR(50),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT chk_payment_credit_claims_reason CHECK (reason IN ('ERROR', 'OVERPAID', 'REFUND_REQUEST', 'OTHER')),
    CONSTRAINT chk_payment_credit_claims_status CHECK (claim_status IN
        ('OPEN', 'KEPT_AS_CREDIT', 'REFUNDED', 'REJECTED', 'WITHDRAWN', 'USED_BY_AGREEMENT')),
    CONSTRAINT chk_payment_credit_claims_amount CHECK (claimed_amount > 0)
);

-- One OPEN claim per payment.
CREATE UNIQUE INDEX uq_payment_credit_claims_one_open
    ON payment_credit_claims (payment_id) WHERE claim_status = 'OPEN';
CREATE INDEX idx_payment_credit_claims_status ON payment_credit_claims (claim_status, opened_at DESC);

CREATE TRIGGER trg_payment_credit_claims_updated_at
    BEFORE UPDATE ON payment_credit_claims
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE payment_credit_claims IS
    'Reclamo sobre el saldo restante de un pago aprobado (error, pago de más, reembolso); congela ese monto de la aplicación automática.';

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('payment_credit_claim', 'Reclamos de pagos', 'payment_credit_claims')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── 4. Permissions ─────────────────────────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COLLECTION_CLAIM_CREATE_OWN', 'PAYMENTS', 'Reportar un reclamo sobre un pago propio'),
    ('COLLECTION_CLAIM_CREATE',     'PAYMENTS', 'Registrar un reclamo sobre el pago de otra persona'),
    ('COLLECTION_CLAIM_VIEW_ALL',   'PAYMENTS', 'Ver los reclamos sobre pagos'),
    ('COLLECTION_CLAIM_RESOLVE',    'PAYMENTS', 'Resolver un reclamo sobre un pago (dejar como saldo, reembolsar o rechazar)'),
    ('PERSON_CREDIT_HOLD_UPDATE',   'MEMBERS',  'Congelar o liberar el saldo a favor de una persona')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('COLLECTION_CLAIM_CREATE', 'COLLECTION_CLAIM_VIEW_ALL', 'COLLECTION_CLAIM_RESOLVE',
                 'PERSON_CREDIT_HOLD_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('AFILIADO', 'PROMOTOR', 'ALIADO') AND p.name = 'COLLECTION_CLAIM_CREATE_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Daily job: apply credit after the charges are generated (02:00) ────────
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters)
VALUES (
    'CREDIT_AUTO_APPLY',
    'Aplicación automática de saldo a favor',
    'Cubre con el saldo a favor de la persona los meses completos de las membresías ACTIVE, del más antiguo al más nuevo, sin cobros parciales.',
    '0 30 2 * * *',
    'America/Caracas',
    '{}'::jsonb
);

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('COLLECTION_CLAIM_CREATE_OWN'), ('COLLECTION_CLAIM_CREATE'), ('COLLECTION_CLAIM_VIEW_ALL'),
            ('COLLECTION_CLAIM_RESOLVE'), ('PERSON_CREDIT_HOLD_UPDATE')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V205: one or more new permissions were not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'CREDIT_AUTO_APPLY') THEN
        RAISE EXCEPTION 'V205: CREDIT_AUTO_APPLY was not seeded';
    END IF;
END $$;
