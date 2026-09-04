SET search_path TO app, public;

-- ============================================================
-- V86: organizations — the organization/company master (ADR 0015 §4).
--
-- Single row today (Centro Óptico Vicente / Grupo Médico 11:11),
-- shaped to survive an eventual multi-tenant future without rework.
-- This is where the VES/USD duality the ADR is about actually lives:
--
--   - official_currency_id   → moneda de curso legal en el país (VES) —
--                              the one facturación/reportes fiscales require.
--   - reference_currency_id  → divisa principal de cotización (USD, per
--                              ADR 0008) — the one plans/commissions/prizes
--                              are denominated in.
--
-- In practice the pair the system consults most in exchange_rates is
-- reference (base) → official (quote), but exchange_rates itself stays
-- generic (V85) and is not coupled to these two columns.
-- ============================================================

CREATE TABLE organizations
(
    organizations_id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    name                    VARCHAR(150) NOT NULL,
    legal_name              VARCHAR(200),
    -- RIF (J-XXXXXXXX-X). Deliberately NOT `tax_id` — that would read like
    -- the table's own BIGINT internal id under the ADR 0006 `{table}_id`
    -- convention.
    tax_identifier          VARCHAR(20),
    logo_key                VARCHAR(255), -- Cloudflare R2 object key

    official_currency_id    BIGINT       NOT NULL REFERENCES currencies (currencies_id),
    reference_currency_id   BIGINT       NOT NULL REFERENCES currencies (currencies_id),

    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,

    CONSTRAINT chk_organizations_currencies_distinct
        CHECK (official_currency_id <> reference_currency_id)
);

CREATE INDEX idx_organizations_is_active ON organizations (is_active) WHERE is_active;

CREATE TRIGGER trg_organizations_updated_at
    BEFORE UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed the single tenant row. VES is the country's legal tender (ADR 0010);
-- USD is the pricing/reference currency (ADR 0008).
INSERT INTO organizations (name, legal_name, official_currency_id, reference_currency_id)
SELECT 'Centro Óptico Vicente',
       'Grupo Médico 11:11',
       (SELECT currencies_id FROM currencies WHERE code = 'VES'),
       (SELECT currencies_id FROM currencies WHERE code = 'USD');
