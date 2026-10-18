SET search_path TO app, public;

-- ============================================================================
-- V182: advisor dossier ("Contrato de Mandato y Representación Comercial
-- Independiente", OB360) — the client's advisor (internally `promoter`) file
-- has five sections; this migration covers the parts that had no home yet:
--
--   I.   Registro         — joined_at + assigned_zone on `promoters`
--                           (code = referral_code and rank = rank_id already exist).
--   II.  Identidad        — already on `persons` (V15/V29), reached through
--                           promoters.person_id; no DDL needed here.
--   III. Perfil ejecutivo — education_level, profession, years_of_experience,
--                           last_company, last_position, vehicle on `promoters`.
--   IV.  Referencias      — new 1..n table `promoter_references`.
--   V.   Datos de cobro   — new 1:1 table `promoter_payout_profiles`, the
--                           default destination used to prefill OUT payouts.
--
-- Plus the `UBII` payment method (Ubii wallet), required by section V.
--
-- Permissions: no new ones. Both sub-resources reuse PROMOTER_VIEW_ALL /
-- PROMOTER_UPDATE (admin) and PROMOTER_VIEW_OWN (advisor self-service) — the
-- dossier is part of managing the promoter, same reasoning the beneficiaries
-- sub-resource uses with MEMBER_*.
-- ============================================================================

-- ─── 1. promoters: registration + executive profile ─────────────────────────

ALTER TABLE promoters
    ADD COLUMN joined_at           DATE,
    ADD COLUMN assigned_zone       VARCHAR(120),
    ADD COLUMN education_level     VARCHAR(20),
    ADD COLUMN profession          VARCHAR(120),
    ADD COLUMN years_of_experience SMALLINT,
    ADD COLUMN last_company        VARCHAR(150),
    ADD COLUMN last_position       VARCHAR(100),
    ADD COLUMN vehicle             VARCHAR(120);

ALTER TABLE promoters
    ADD CONSTRAINT ck_promoters_education_level
        CHECK (education_level IS NULL
            OR education_level IN ('PRIMARY', 'SECONDARY', 'TECHNICAL', 'UNIVERSITY', 'POSTGRADUATE')),
    ADD CONSTRAINT ck_promoters_years_of_experience
        CHECK (years_of_experience IS NULL OR years_of_experience >= 0);

-- Existing rows: the best available proxy for the joining date is the row's
-- creation date, in the business timezone (ADR 0010).
UPDATE promoters
SET joined_at = (created_at AT TIME ZONE 'America/Caracas')::date
WHERE joined_at IS NULL;

COMMENT ON COLUMN promoters.joined_at IS 'Fecha de ingreso del asesor (expediente, sección I)';
COMMENT ON COLUMN promoters.assigned_zone IS 'Región/zona asignada (texto libre)';
COMMENT ON COLUMN promoters.education_level IS 'Nivel académico: PRIMARY, SECONDARY, TECHNICAL, UNIVERSITY, POSTGRADUATE';
COMMENT ON COLUMN promoters.vehicle IS 'Vehículo propio (texto libre: marca/modelo/año, o vacío si no tiene)';

-- ─── 2. promoter_references (1..n personal references) ──────────────────────

CREATE TABLE promoter_references
(
    promoter_references_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                   UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    promoter_id            BIGINT       NOT NULL REFERENCES promoters (promoters_id) ON DELETE CASCADE,

    first_name             VARCHAR(50)  NOT NULL,
    last_name              VARCHAR(50)  NOT NULL,
    -- Optional cédula, same V/E pair as persons (V15).
    document_type          VARCHAR(1),
    document_number        VARCHAR(20),
    phone                  VARCHAR(30),

    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status                 VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,

    CONSTRAINT ck_promoter_references_document_type
        CHECK (document_type IS NULL OR document_type IN ('V', 'E')),
    CONSTRAINT ck_promoter_references_document_pair
        CHECK ((document_type IS NULL AND document_number IS NULL)
            OR (document_type IS NOT NULL AND document_number IS NOT NULL)),
    CONSTRAINT ck_promoter_references_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_promoter_references_promoter ON promoter_references (promoter_id);

CREATE TRIGGER trg_promoter_references_updated_at
    BEFORE UPDATE ON promoter_references
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE promoter_references IS 'Referencias personales del asesor (expediente, sección IV)';

-- ─── 3. promoter_payout_profiles (1:1 payout destination) ───────────────────

CREATE TABLE promoter_payout_profiles
(
    promoter_payout_profiles_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    promoter_id                    BIGINT       NOT NULL UNIQUE REFERENCES promoters (promoters_id) ON DELETE CASCADE,

    -- National bank account (20-digit Venezuelan account number).
    bank_id                        BIGINT       REFERENCES banks (banks_id),
    account_number                 VARCHAR(20),
    -- Cédula/RIF the account (and Pago Móvil) is registered to.
    account_holder_document_type   VARCHAR(1),
    account_holder_document_number VARCHAR(20),

    -- Pago Móvil.
    mobile_payment_phone           VARCHAR(30),
    mobile_payment_bank_id         BIGINT       REFERENCES banks (banks_id),

    -- Ubii wallet.
    ubii_account                   VARCHAR(60),
    ubii_holder_name               VARCHAR(150),

    is_active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    status                         VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                     UUID,
    updated_by                     UUID,

    CONSTRAINT ck_promoter_payout_profiles_account_number
        CHECK (account_number IS NULL OR account_number ~ '^[0-9]{20}$'),
    CONSTRAINT ck_promoter_payout_profiles_holder_document_type
        CHECK (account_holder_document_type IS NULL
            OR account_holder_document_type IN ('J', 'V', 'E', 'G', 'P')),
    CONSTRAINT ck_promoter_payout_profiles_holder_document_pair
        CHECK ((account_holder_document_type IS NULL AND account_holder_document_number IS NULL)
            OR (account_holder_document_type IS NOT NULL AND account_holder_document_number IS NOT NULL)),
    CONSTRAINT ck_promoter_payout_profiles_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TRIGGER trg_promoter_payout_profiles_updated_at
    BEFORE UPDATE ON promoter_payout_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE promoter_payout_profiles IS 'Datos de cobro del asesor para liquidación (expediente, sección V); 1:1 con promoters';

-- ─── 4. UBII payment method ─────────────────────────────────────────────────
-- Wallet method: no bank / account type+code; the Ubii account itself goes
-- in the generic account identifier (like INTERNATIONAL_TRANSFER, V136),
-- with holder identification and a reference number as proof — same review
-- flow as PAGO_MOVIL (requires_approval default TRUE, V158).
INSERT INTO payment_methods (uuid, code, name, description,
                             is_mandatory_identification, is_mandatory_bank,
                             is_mandatory_bank_account, is_mandatory_account_type, is_mandatory_account_code,
                             is_mandatory_phone, is_mandatory_email, is_mandatory_reference_number,
                             requires_approval)
VALUES (gen_random_uuid(), 'UBII', 'Ubii', 'Pago a través de la billetera Ubii.',
        TRUE, FALSE,
        TRUE, FALSE, FALSE,
        FALSE, FALSE, TRUE,
        TRUE)
ON CONFLICT (code) DO NOTHING;

-- ─── 5. entity_config (audit registration) ──────────────────────────────────

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('promoter_reference',      'Referencias de asesores',   'promoter_references'),
    ('promoter_payout_profile', 'Datos de cobro de asesores', 'promoter_payout_profiles')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payment_methods WHERE code = 'UBII') THEN
        RAISE EXCEPTION 'V182: UBII payment method was not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES ('promoter_reference'), ('promoter_payout_profile')) AS want(entity_key)
        WHERE NOT EXISTS (SELECT 1 FROM entity_config c WHERE c.entity_key = want.entity_key)
    ) THEN
        RAISE EXCEPTION 'V182: one or more entity_config rows were not created';
    END IF;
END $$;
