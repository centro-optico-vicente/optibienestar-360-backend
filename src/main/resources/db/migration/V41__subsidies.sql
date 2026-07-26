SET search_path TO app, public;

-- V41: Subsidies & Exonerations (v2 PDF item #1 "Gestión de Subsidios y
-- Exoneraciones" + #1.b "Motor de Solvencia"). Controls to waive or subsidize
-- payments for special profiles (foundations, churches, low-income cases) while
-- keeping a formal audit trail.
--
-- Expanded model (beyond the reserved vertical-12 sketch):
--   * Two INDEPENDENT coverages per fee type — monthly_percentage +
--     inscription_percentage (0-100 each, NULL = that fee is not covered,
--     100 = full exoneration, 0<X<100 = partial subsidy). This absorbs the
--     single "percentage" the checklist had reserved.
--   * valid_until (nullable) → time-limited subsidies.
--   * max_exonerated_beneficiaries → a beneficiary exoneration cap distinct
--     from the plan's own max_beneficiaries.
--   * subsidy_beneficiaries → optional per-beneficiary exoneration, each with
--     its own monthly/inscription coverage.
--
-- Five schema changes ship here, backing the code in the same PR:
--   1. subsidies              — the titular's subsidy.
--   2. subsidy_beneficiaries  — per-beneficiary exoneration (optional).
--   3. subsidy_audit_log      — immutable CREATED/MODIFIED/REVOKED trail.
--   4. payments.discount_*    — one-off discount on a single PENDING payment.
--   5. SUBSIDIES permission domain + 4 permissions.


-- ─── 1. subsidies ────────────────────────────────────────────────────────────
CREATE TABLE subsidies
(
    subsidies_id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                         UUID         NOT NULL UNIQUE,

    member_id                    BIGINT       NOT NULL REFERENCES members (members_id),

    -- Independent coverage per fee type. NULL = fee not covered; 100 = full
    -- exoneration; 0 < X < 100 = partial subsidy of X%.
    monthly_percentage           NUMERIC(5, 2)
        CONSTRAINT chk_subsidies_monthly_pct
            CHECK (monthly_percentage IS NULL OR (monthly_percentage >= 0 AND monthly_percentage <= 100)),
    inscription_percentage       NUMERIC(5, 2)
        CONSTRAINT chk_subsidies_inscription_pct
            CHECK (inscription_percentage IS NULL OR (inscription_percentage >= 0 AND inscription_percentage <= 100)),

    -- How many beneficiaries this subsidy may exonerate — distinct from the
    -- plan's own max_beneficiaries. NULL = bounded only by the plan.
    max_exonerated_beneficiaries INT
        CONSTRAINT chk_subsidies_max_exon
            CHECK (max_exonerated_beneficiaries IS NULL OR max_exonerated_beneficiaries >= 0),

    reason                       TEXT         NOT NULL,
    authorized_by                BIGINT       REFERENCES users (users_id),

    valid_from                   DATE         NOT NULL,
    valid_until                  DATE,                       -- NULL = indefinite

    is_active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    status                       VARCHAR(50),
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                   UUID,
    updated_by                   UUID,

    -- A subsidy must cover at least one fee type.
    CONSTRAINT chk_subsidies_covers_something
        CHECK (monthly_percentage IS NOT NULL OR inscription_percentage IS NOT NULL),
    -- Coherent validity window.
    CONSTRAINT chk_subsidies_valid_window
        CHECK (valid_until IS NULL OR valid_until >= valid_from)
);

CREATE TRIGGER trg_subsidies_updated_at
    BEFORE UPDATE ON subsidies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- "Does member X have an active subsidy on date D?" — the resolver's hot path
-- (solvency sweep + amount calc). Partial: SENT-equivalent bulk stays out of scan.
CREATE INDEX idx_subsidies_member_window
    ON subsidies (member_id, valid_from, valid_until) WHERE is_active;


-- ─── 2. subsidy_beneficiaries ────────────────────────────────────────────────
-- Optional per-beneficiary exoneration under a subsidy. Same two-percentage
-- shape as the titular. Bounded by subsidies.max_exonerated_beneficiaries
-- (service-enforced — the CHECK can't count sibling rows).
CREATE TABLE subsidy_beneficiaries
(
    subsidy_beneficiaries_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                     UUID         NOT NULL UNIQUE,

    subsidy_id               BIGINT       NOT NULL REFERENCES subsidies (subsidies_id) ON DELETE CASCADE,
    beneficiary_id           BIGINT       NOT NULL REFERENCES beneficiaries (beneficiaries_id),

    monthly_percentage       NUMERIC(5, 2)
        CONSTRAINT chk_subsidy_ben_monthly_pct
            CHECK (monthly_percentage IS NULL OR (monthly_percentage >= 0 AND monthly_percentage <= 100)),
    inscription_percentage   NUMERIC(5, 2)
        CONSTRAINT chk_subsidy_ben_inscription_pct
            CHECK (inscription_percentage IS NULL OR (inscription_percentage >= 0 AND inscription_percentage <= 100)),

    is_active                BOOLEAN      NOT NULL DEFAULT TRUE,
    status                   VARCHAR(50),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID,

    CONSTRAINT uq_subsidy_beneficiaries UNIQUE (subsidy_id, beneficiary_id),
    CONSTRAINT chk_subsidy_ben_covers_something
        CHECK (monthly_percentage IS NOT NULL OR inscription_percentage IS NOT NULL)
);

CREATE TRIGGER trg_subsidy_beneficiaries_updated_at
    BEFORE UPDATE ON subsidy_beneficiaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_subsidy_beneficiaries_subsidy     ON subsidy_beneficiaries (subsidy_id);
CREATE INDEX idx_subsidy_beneficiaries_beneficiary ON subsidy_beneficiaries (beneficiary_id) WHERE is_active;


-- ─── 3. subsidy_audit_log ────────────────────────────────────────────────────
-- Immutable trail — "registro formal en auditoría" (PDF). Append-only by
-- convention (no UPDATE path in code); before/after snapshots as JSONB. Keeps
-- the BaseEntity columns for uniformity; created_at IS the action timestamp.
CREATE TABLE subsidy_audit_log
(
    subsidy_audit_log_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                 UUID         NOT NULL UNIQUE,

    subsidy_id           BIGINT       NOT NULL REFERENCES subsidies (subsidies_id),
    action               VARCHAR(20)  NOT NULL
        CONSTRAINT chk_subsidy_audit_action CHECK (action IN ('CREATED', 'MODIFIED', 'REVOKED')),
    actor_id             BIGINT       REFERENCES users (users_id),
    before_json          JSONB,
    after_json           JSONB,
    reason               TEXT,

    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(50),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID
);

CREATE TRIGGER trg_subsidy_audit_log_updated_at
    BEFORE UPDATE ON subsidy_audit_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_subsidy_audit_log_subsidy ON subsidy_audit_log (subsidy_id, created_at DESC);


-- ─── 4. payments.discount_* (one-off discount on a single PENDING payment) ────
-- Distinct from a recurring subsidy: condone/reduce ONE payment (ALLOWS_DISCOUNT).
-- Audited inline (who/when/why/how much). A general payment_audit_log is a
-- deferred follow-up — the vertical-12 note assuming it already existed was wrong.
ALTER TABLE payments
    ADD COLUMN discount_amount NUMERIC(10, 2)
        CONSTRAINT chk_payments_discount_amount CHECK (discount_amount IS NULL OR discount_amount >= 0),
    ADD COLUMN discount_reason TEXT,
    ADD COLUMN discounted_by   BIGINT REFERENCES users (users_id),
    ADD COLUMN discounted_at   TIMESTAMPTZ;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_discount_not_exceed
        CHECK (discount_amount IS NULL OR discount_amount <= amount);
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_discount_coherence
        CHECK (discount_amount IS NULL
            OR (discount_reason IS NOT NULL AND discounted_by IS NOT NULL AND discounted_at IS NOT NULL));


-- ─── 5. Permissions (new SUBSIDIES domain) ───────────────────────────────────
-- The V30 trigger auto-grants each to SYSTEM on insert.
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('SUBSIDIES', 'Subsidios y Exoneraciones', 'i-lucide-badge-percent',
        'Subsidios y exoneraciones de pago a perfiles especiales, con auditoría formal', 130);

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('SUBSIDY_APPROVE',  'SUBSIDIES', 'Crear, modificar y revocar subsidios y exoneraciones'),
    ('SUBSIDY_VIEW_ALL', 'SUBSIDIES', 'Ver todos los subsidios y su historial de auditoría'),
    ('SUBSIDY_VIEW_OWN', 'SUBSIDIES', 'Ver los subsidios propios del titular'),
    ('ALLOWS_DISCOUNT',  'SUBSIDIES', 'Aplicar un descuento puntual a un pago pendiente')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- Operational perms → SYSTEM + ADMINISTRADOR (subsidies are an admin action).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('SUBSIDY_APPROVE', 'SUBSIDY_VIEW_ALL', 'ALLOWS_DISCOUNT')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- VIEW_OWN → roles with a personal membership (titular sees own subsidies).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'AFILIADO')
  AND p.name = 'SUBSIDY_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── 6. Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('SUBSIDY_APPROVE'), ('SUBSIDY_VIEW_ALL'), ('SUBSIDY_VIEW_OWN'), ('ALLOWS_DISCOUNT')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V41: one or more SUBSIDY_*/ALLOWS_DISCOUNT permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('SUBSIDY_APPROVE', 'SUBSIDY_VIEW_ALL', 'SUBSIDY_VIEW_OWN', 'ALLOWS_DISCOUNT')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V41: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('SUBSIDY_APPROVE', 'SUBSIDY_VIEW_ALL', 'ALLOWS_DISCOUNT')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V41: ADMINISTRADOR is missing one of the new admin permissions';
    END IF;
END $$;
