SET search_path TO app, public;

-- ============================================================
-- V84: currencies — the currency master (ADR 0015).
--
-- Currency stops being a free VARCHAR(3) column scattered across
-- payments/commissions/plans/etc. and becomes a real catalog with
-- the standard dual identifier (BIGINT PK + uuid, per ADR 0006 —
-- this table is NOT an exception). `code` is the UNIQUE natural
-- key: other tables' FKs point at `currencies_id` (BIGINT), never
-- at `code` directly — the API still resolves the triplet
-- currency_Uuid / currency_Display / currency_Code per ADR 0014,
-- but that convention lives in JSON responses, not DB columns.
-- ============================================================

CREATE TABLE currencies
(
    currencies_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    code           VARCHAR(4)   NOT NULL UNIQUE,   -- ISO 4217 (USD, VES, EUR) or pseudo-code (USDT)
    name           VARCHAR(60)  NOT NULL,
    symbol         VARCHAR(6)   NOT NULL,
    decimal_places SMALLINT     NOT NULL DEFAULT 2 CHECK (decimal_places >= 0),

    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    status         VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX idx_currencies_is_active ON currencies (is_active) WHERE is_active;

CREATE TRIGGER trg_currencies_updated_at
    BEFORE UPDATE ON currencies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed: the currencies the platform already handles per ADR 0008 / ADR 0010.
-- USDT is not seeded yet — added when exchange-rates-api ships support for it.
INSERT INTO currencies (code, name, symbol, decimal_places) VALUES
    ('USD', 'Dólar estadounidense', 'US$', 2),
    ('VES', 'Bolívar venezolano',   'Bs.', 2),
    ('EUR', 'Euro',                 '€',   2);
