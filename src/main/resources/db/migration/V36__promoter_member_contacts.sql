SET search_path TO app, public;

-- V36: delegated collection management (v2 PDF 2.b "Gestión de Cobranza Delegada").
--
-- A promoter logs the collection actions they take on their own portfolio:
-- reminders, payment promises, plain notes. Insert-only audit of outreach — the
-- basis for a future collection-performance bonus, separate from the sales bonus.
-- One new permission gates the write endpoints; the reads reuse PROMOTER_VIEW_OWN
-- (V35). The self-service endpoints resolve the promoter from the JWT and the
-- service verifies the target member belongs to that promoter.


-- ─── 1. Contacts table ──────────────────────────────────────────────────────
-- BaseEntity columns (id/uuid/is_active/status/audit) so the JPA entity validates.
-- created_at is the moment of the outreach; rows are insert-only by convention.
CREATE TABLE promoter_member_contacts
(
    promoter_member_contacts_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                        UUID         NOT NULL UNIQUE,

    promoter_id                 BIGINT       NOT NULL REFERENCES promoters (promoters_id),
    member_id                   BIGINT       NOT NULL REFERENCES members (members_id),

    type                        VARCHAR(20)  NOT NULL
        CONSTRAINT promoter_member_contacts_type_check
            CHECK (type IN ('REMINDER', 'PAYMENT_PROMISE', 'NOTE')),
    note                        TEXT,

    -- Only a PAYMENT_PROMISE carries an amount + a promised date; the other
    -- types must leave both NULL.
    promised_amount             NUMERIC(10, 2),
    promised_at_date            DATE,

    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    status                      VARCHAR(50),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                  UUID,
    updated_by                  UUID,

    CONSTRAINT promoter_member_contacts_promise_coherence CHECK (
        (type =  'PAYMENT_PROMISE' AND promised_amount IS NOT NULL AND promised_at_date IS NOT NULL)
     OR (type <> 'PAYMENT_PROMISE' AND promised_amount IS NULL     AND promised_at_date IS NULL)
    ),
    CONSTRAINT promoter_member_contacts_amount_positive CHECK (
        promised_amount IS NULL OR promised_amount > 0
    )
);

-- History of a promoter's outreach to one member, newest first.
CREATE INDEX idx_promoter_member_contacts_promoter_member
    ON promoter_member_contacts (promoter_id, member_id, created_at DESC);


-- ─── 2. Permission (write) ──────────────────────────────────────────────────
-- Reads (contact history + collection score) reuse PROMOTER_VIEW_OWN (V35).
-- The V30 trigger auto-grants this to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT 'PROMOTER_CONTACT_OWN', pd.permission_domains_id,
       'Registrar gestiones de cobranza sobre la cartera propia (recordatorios, promesas de pago)'
FROM permission_domains pd
WHERE pd.code = 'PROMOTERS';


-- ADMINISTRADOR + PROMOTOR can log collection contacts.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('ADMINISTRADOR', 'PROMOTOR')
  AND p.name = 'PROMOTER_CONTACT_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 3. Fail loudly rather than migrate into a half-applied state ────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'PROMOTER_CONTACT_OWN') THEN
        RAISE EXCEPTION 'V36: PROMOTER_CONTACT_OWN was not created';
    END IF;

    -- SYSTEM (V30 trigger) + ADMINISTRADOR + PROMOTOR must all hold it.
    IF EXISTS (
        SELECT 1 FROM (VALUES ('SYSTEM'), ('ADMINISTRADOR'), ('PROMOTOR')) AS req(name)
        WHERE NOT EXISTS (
            SELECT 1
            FROM role_permissions rp
                     JOIN roles r       ON r.roles_id = rp.role_id AND r.name = req.name
                     JOIN permissions p  ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_CONTACT_OWN')
    ) THEN
        RAISE EXCEPTION 'V36: PROMOTER_CONTACT_OWN not granted to SYSTEM/ADMINISTRADOR/PROMOTOR';
    END IF;
END $$;
