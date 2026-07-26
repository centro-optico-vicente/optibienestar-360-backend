SET search_path TO app, public;

-- V38: corporate contracts (v2 PDF — "Contratos Corporativos"). An institution
-- signs one contract against the CORPORATIVO plan and enrolls its people under it.
--
-- Billing hinges on payer_mode:
--   INSTITUTION_BULK  → the institution is billed (payments carry
--                       corporate_contract_id + the contract's contact user as payer).
--   INDIVIDUAL_PAYER  → each member pays their own membership like any affiliate;
--                       the contract is just the grouping.
--
-- Three schema changes ship here, backing the code in the same PR:
--   1. corporate_contracts (the contract).
--   2. members.corporate_contract_id  — the member↔contract link (NULL for
--      Individual/Familiar affiliates).
--   3. payments.corporate_contract_id — marks a payment billed to the institution.
-- Plus two permissions (MEMBERS domain) gating the admin endpoints.


-- ─── 1. corporate_contracts ──────────────────────────────────────────────────
CREATE TABLE corporate_contracts
(
    corporate_contracts_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE,

    -- Must reference a CORPORATIVO plan; enforced service-side (the CHECK can't
    -- reach across to plans.type).
    plan_id                BIGINT       NOT NULL REFERENCES plans (plans_id),

    institution_name       VARCHAR(200) NOT NULL,
    institution_tax_id     VARCHAR(20)  NOT NULL,   -- RIF

    -- The institution's point of contact as a platform user (optional — the
    -- contract may be managed centrally without a dedicated login).
    contact_user_id        BIGINT       REFERENCES users (users_id),

    payer_mode             VARCHAR(20)  NOT NULL
        CONSTRAINT chk_corporate_contracts_payer_mode
            CHECK (payer_mode IN ('INSTITUTION_BULK', 'INDIVIDUAL_PAYER')),

    -- Planned headcount (nullable) vs the running enrolled count (maintained by
    -- the bulk-enroll service).
    expected_member_count  INT          CHECK (expected_member_count IS NULL OR expected_member_count >= 0),
    actual_member_count    INT          NOT NULL DEFAULT 0
        CONSTRAINT chk_corporate_contracts_actual_count CHECK (actual_member_count >= 0),

    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             UUID,
    updated_by             UUID
);

CREATE TRIGGER trg_corporate_contracts_updated_at
    BEFORE UPDATE ON corporate_contracts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_corporate_contracts_plan    ON corporate_contracts (plan_id);
CREATE INDEX idx_corporate_contracts_active  ON corporate_contracts (created_at DESC) WHERE is_active;


-- ─── 2. members.corporate_contract_id ────────────────────────────────────────
ALTER TABLE members
    ADD COLUMN corporate_contract_id BIGINT REFERENCES corporate_contracts (corporate_contracts_id);

-- Portfolio of a contract ("list this contract's members").
CREATE INDEX idx_members_corporate_contract
    ON members (corporate_contract_id) WHERE corporate_contract_id IS NOT NULL;


-- ─── 3. payments.corporate_contract_id ───────────────────────────────────────
-- Set only for INSTITUTION_BULK payments — the money is billed to the contract.
ALTER TABLE payments
    ADD COLUMN corporate_contract_id BIGINT REFERENCES corporate_contracts (corporate_contracts_id);

CREATE INDEX idx_payments_corporate_contract
    ON payments (corporate_contract_id) WHERE corporate_contract_id IS NOT NULL;


-- ─── 4. Permissions (MEMBERS domain) ─────────────────────────────────────────
-- The V30 trigger auto-grants each to SYSTEM on insert.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('CORPORATE_CONTRACT_MANAGE',   'MEMBERS', 'Gestionar contratos corporativos y altas en bloque'),
    ('CORPORATE_CONTRACT_VIEW_ALL', 'MEMBERS', 'Ver contratos corporativos y sus miembros')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
  AND p.name IN ('CORPORATE_CONTRACT_MANAGE', 'CORPORATE_CONTRACT_VIEW_ALL')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 5. Fail loudly rather than migrate into a half-applied state ────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('CORPORATE_CONTRACT_MANAGE'), ('CORPORATE_CONTRACT_VIEW_ALL')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V38: one or more CORPORATE_CONTRACT_* permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('CORPORATE_CONTRACT_MANAGE', 'CORPORATE_CONTRACT_VIEW_ALL')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V38: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('CORPORATE_CONTRACT_MANAGE', 'CORPORATE_CONTRACT_VIEW_ALL')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V38: ADMINISTRADOR is missing one of the new permissions';
    END IF;
END $$;
