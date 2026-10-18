SET search_path TO app, public;

-- ============================================================================
-- V184: advisor payout accounts — N per advisor (internally `promoter`),
-- replacing the 1:1 `promoter_payout_profiles` from V182 (dossier section V).
--
-- Why N instead of 1:1: an advisor can be paid by bank transfer, Pago Móvil,
-- Ubii, Zelle… and the payment management may change method at any time.
-- Each row is ONE destination whose kind comes from the configurable
-- `payment_methods` catalog (V115), so a new method needs no schema/code
-- change: which fields a row needs is driven by the method's
-- `is_mandatory_*` flags (V115/V130/V134/V136, validated in the service),
-- the same flags the payment-line forms already use. At most one active row
-- per advisor is the default (partial unique index).
--
-- Columns mirror the payment-line destination fields (V129) so a payout can
-- be prefilled 1:1 from an account:
--   identification     ← holder_document_type + holder_document_number
--   bank               ← bank_id
--   bank_account_type  ← account_type   (CORRIENTE / AHORRO, frontend options)
--   bank_account_code  ← account_number (20-digit VE account, service-validated)
--   bank_account_identifier ← account_identifier (Ubii, international, wallets)
--   phone / email      ← phone / email
--
-- Data: each active V182 profile is split into up to three accounts
-- (bank account → BANK_TRANSFER, Pago Móvil → PAGO_MOVIL, Ubii → UBII); the
-- first one created is the default. Then the V182 table and its
-- entity_config row are dropped.
--
-- Permissions: dedicated, granular PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL /
-- _CREATE / _UPDATE (also "set as default") / _DELETE (payout destinations
-- are sensitive financial data — not every role that edits a promoter should
-- see them), granted to the roles that already manage OUT payments (SYSTEM,
-- ADMINISTRADOR, OPERADOR — V129/V132); plus _VIEW_OWN for the advisor's own
-- read-only list, granted to PROMOTOR (and SYSTEM).
-- ============================================================================

-- ─── 1. promoter_payout_accounts ────────────────────────────────────────────

CREATE TABLE promoter_payout_accounts
(
    promoter_payout_accounts_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                        UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    promoter_id                 BIGINT       NOT NULL REFERENCES promoters (promoters_id) ON DELETE CASCADE,
    payment_method_id           BIGINT       NOT NULL REFERENCES payment_methods (payment_methods_id),

    -- Optional user-facing alias ("Cuenta nómina", "Zelle personal"…).
    label                       VARCHAR(60),

    bank_id                     BIGINT       REFERENCES banks (banks_id),
    account_type                VARCHAR(20),
    -- National account number. VARCHAR(34) leaves room for longer formats;
    -- the 20-digit Venezuelan rule depends on the method, so it is enforced
    -- in the service, not here.
    account_number              VARCHAR(34),
    -- Free-form destination for methods without a bank account breakdown
    -- (Ubii account, international account/IBAN, other wallets).
    account_identifier          VARCHAR(120),

    holder_name                 VARCHAR(150),
    holder_document_type        VARCHAR(1),
    holder_document_number      VARCHAR(20),

    phone                       VARCHAR(30),
    email                       VARCHAR(160),

    is_default                  BOOLEAN      NOT NULL DEFAULT FALSE,

    is_active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    status                      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,

    CONSTRAINT ck_promoter_payout_accounts_account_type
        CHECK (account_type IS NULL OR account_type IN ('CORRIENTE', 'AHORRO')),
    CONSTRAINT ck_promoter_payout_accounts_holder_document_type
        CHECK (holder_document_type IS NULL OR holder_document_type IN ('J', 'V', 'E', 'G', 'P')),
    CONSTRAINT ck_promoter_payout_accounts_holder_document_pair
        CHECK ((holder_document_type IS NULL AND holder_document_number IS NULL)
            OR (holder_document_type IS NOT NULL AND holder_document_number IS NOT NULL)),
    CONSTRAINT ck_promoter_payout_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_promoter_payout_accounts_promoter ON promoter_payout_accounts (promoter_id);
CREATE INDEX idx_promoter_payout_accounts_payment_method ON promoter_payout_accounts (payment_method_id);
CREATE INDEX idx_promoter_payout_accounts_bank ON promoter_payout_accounts (bank_id) WHERE bank_id IS NOT NULL;

-- At most one default among the advisor's active accounts.
CREATE UNIQUE INDEX uq_promoter_payout_accounts_default
    ON promoter_payout_accounts (promoter_id)
    WHERE is_default AND is_active;

CREATE TRIGGER trg_promoter_payout_accounts_updated_at
    BEFORE UPDATE ON promoter_payout_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE promoter_payout_accounts IS 'Cuentas de cobro del asesor (expediente, sección V); N por asesor, una predeterminada';
COMMENT ON COLUMN promoter_payout_accounts.payment_method_id IS 'Tipo de cuenta según el catálogo payment_methods (transferencia, pago móvil, Ubii, Zelle…)';
COMMENT ON COLUMN promoter_payout_accounts.account_type IS 'Tipo de cuenta bancaria: CORRIENTE o AHORRO';
COMMENT ON COLUMN promoter_payout_accounts.account_identifier IS 'Identificador libre (cuenta Ubii, cuenta internacional, billetera)';
COMMENT ON COLUMN promoter_payout_accounts.is_default IS 'Cuenta predeterminada para prellenar pagos de salida (máximo una activa por asesor)';

-- ─── 2. Data migration from promoter_payout_profiles (V182) ─────────────────

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('BANK_TRANSFER'), ('PAGO_MOVIL'), ('UBII')) AS want(code)
        WHERE NOT EXISTS (SELECT 1 FROM payment_methods m WHERE m.code = want.code)
    ) THEN
        RAISE EXCEPTION 'V184: payment methods BANK_TRANSFER / PAGO_MOVIL / UBII must exist before migrating payout profiles';
    END IF;
END $$;

-- Soft-deleted profiles (is_active = FALSE) already read as "no profile" in
-- V182's API, so they are not carried over.
WITH src AS (
    SELECT p.promoter_id,
           1                                AS ord,
           'BANK_TRANSFER'                  AS method_code,
           p.bank_id                        AS bank_id,
           p.account_number::VARCHAR(34)    AS account_number,
           NULL::VARCHAR(120)               AS account_identifier,
           NULL::VARCHAR(30)                AS phone,
           NULL::VARCHAR(150)               AS holder_name,
           p.account_holder_document_type   AS holder_document_type,
           p.account_holder_document_number AS holder_document_number,
           p.created_by,
           p.updated_by
    FROM promoter_payout_profiles p
    WHERE p.is_active AND (p.bank_id IS NOT NULL OR p.account_number IS NOT NULL)
    UNION ALL
    SELECT p.promoter_id, 2, 'PAGO_MOVIL',
           p.mobile_payment_bank_id, NULL, NULL, p.mobile_payment_phone, NULL,
           p.account_holder_document_type, p.account_holder_document_number,
           p.created_by, p.updated_by
    FROM promoter_payout_profiles p
    WHERE p.is_active AND (p.mobile_payment_phone IS NOT NULL OR p.mobile_payment_bank_id IS NOT NULL)
    UNION ALL
    SELECT p.promoter_id, 3, 'UBII',
           NULL, NULL, p.ubii_account, NULL, p.ubii_holder_name,
           p.account_holder_document_type, p.account_holder_document_number,
           p.created_by, p.updated_by
    FROM promoter_payout_profiles p
    WHERE p.is_active AND (p.ubii_account IS NOT NULL OR p.ubii_holder_name IS NOT NULL)
),
ranked AS (
    SELECT src.*, ROW_NUMBER() OVER (PARTITION BY src.promoter_id ORDER BY src.ord) AS rn
    FROM src
)
INSERT INTO promoter_payout_accounts (promoter_id, payment_method_id, bank_id, account_number,
                                      account_identifier, phone, holder_name,
                                      holder_document_type, holder_document_number,
                                      is_default, created_by, updated_by)
SELECT r.promoter_id, m.payment_methods_id, r.bank_id, r.account_number,
       r.account_identifier, r.phone, r.holder_name,
       r.holder_document_type, r.holder_document_number,
       r.rn = 1, r.created_by, r.updated_by
FROM ranked r
JOIN payment_methods m ON m.code = r.method_code;

DO $$
DECLARE
    expected BIGINT;
    migrated BIGINT;
BEGIN
    SELECT COUNT(*) FILTER (WHERE bank_id IS NOT NULL OR account_number IS NOT NULL)
         + COUNT(*) FILTER (WHERE mobile_payment_phone IS NOT NULL OR mobile_payment_bank_id IS NOT NULL)
         + COUNT(*) FILTER (WHERE ubii_account IS NOT NULL OR ubii_holder_name IS NOT NULL)
    INTO expected
    FROM promoter_payout_profiles
    WHERE is_active;

    SELECT COUNT(*) INTO migrated FROM promoter_payout_accounts;

    IF expected <> migrated THEN
        RAISE EXCEPTION 'V184: expected % migrated payout accounts, got %', expected, migrated;
    END IF;
END $$;

-- ─── 3. Drop the 1:1 profile (V182) ─────────────────────────────────────────

DROP TABLE promoter_payout_profiles;

DELETE FROM entity_config WHERE entity_key = 'promoter_payout_profile';

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('promoter_payout_account', 'Cuentas de cobro de asesores', 'promoter_payout_accounts')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── 4. Permissions ─────────────────────────────────────────────────────────
-- Granular per action, like every other module (no generic _MANAGE).

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL', 'Ver las cuentas de cobro de cualquier asesor'),
    ('PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN', 'Ver las cuentas de cobro propias (portal del asesor)'),
    ('PROMOTER_PAYOUT_ACCOUNT_CREATE',   'Registrar una cuenta de cobro de un asesor'),
    ('PROMOTER_PAYOUT_ACCOUNT_UPDATE',   'Editar una cuenta de cobro de un asesor y marcarla como predeterminada'),
    ('PROMOTER_PAYOUT_ACCOUNT_DELETE',   'Eliminar una cuenta de cobro de un asesor')
) AS v(name, description)
JOIN permission_domains pd ON pd.code = 'PROMOTERS'
ON CONFLICT (name) DO NOTHING;

-- Admin side: the roles that already manage OUT payments (V129/V132).
-- SYSTEM also gets every permission through the V30 autosync trigger;
-- listed explicitly so the grant does not depend on it.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL', 'PROMOTER_PAYOUT_ACCOUNT_CREATE',
                 'PROMOTER_PAYOUT_ACCOUNT_UPDATE', 'PROMOTER_PAYOUT_ACCOUNT_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Advisor self-service (read-only own accounts).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'PROMOTOR')
  AND p.name = 'PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'app' AND tablename = 'promoter_payout_profiles') THEN
        RAISE EXCEPTION 'V184: promoter_payout_profiles was not dropped';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM entity_config WHERE entity_key = 'promoter_payout_account') THEN
        RAISE EXCEPTION 'V184: entity_config row promoter_payout_account was not created';
    END IF;

    IF (SELECT COUNT(*) FROM permissions
        WHERE name IN ('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL', 'PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN',
                       'PROMOTER_PAYOUT_ACCOUNT_CREATE', 'PROMOTER_PAYOUT_ACCOUNT_UPDATE',
                       'PROMOTER_PAYOUT_ACCOUNT_DELETE')) <> 5 THEN
        RAISE EXCEPTION 'V184: payout account permissions were not created (PROMOTERS domain missing?)';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (VALUES ('SYSTEM'), ('ADMINISTRADOR')) AS want_role(name)
        CROSS JOIN (VALUES ('PROMOTER_PAYOUT_ACCOUNT_VIEW_ALL'), ('PROMOTER_PAYOUT_ACCOUNT_CREATE'),
                           ('PROMOTER_PAYOUT_ACCOUNT_UPDATE'), ('PROMOTER_PAYOUT_ACCOUNT_DELETE')) AS want_perm(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM role_permissions rp
            JOIN roles r ON r.roles_id = rp.role_id AND r.name = want_role.name
            JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = want_perm.name
        )
    ) THEN
        RAISE EXCEPTION 'V184: SYSTEM/ADMINISTRADOR did not receive the payout account permissions';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM role_permissions rp
        JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'PROMOTOR'
        JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN'
    ) THEN
        RAISE EXCEPTION 'V184: PROMOTOR did not receive PROMOTER_PAYOUT_ACCOUNT_VIEW_OWN';
    END IF;
END $$;
