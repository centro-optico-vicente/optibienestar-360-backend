SET search_path TO app, public;

-- ============================================================
-- V85: exchange_rates — historical exchange-rate ledger (ADR 0015 §2).
--
-- A time series, not a mutable single value. The BCV publishes a
-- rate ("Fecha Operación") that becomes vigente the next business
-- day at 8 AM America/Caracas ("Fecha Valor") — a Friday publish
-- stays vigente all weekend until Monday 8 AM. That calendar
-- complexity is resolved ONCE, at ingestion (FetchExchangeRatesJob,
-- Tarea 2.13), by computing `valid_from` correctly when the row is
-- inserted. Reads never re-derive it: "the current rate" is always
--
--     SELECT * FROM exchange_rates
--     WHERE base_currency_id = :base AND quote_currency_id = :quote
--       AND valid_from <= :at
--     ORDER BY valid_from DESC LIMIT 1
--
-- base_currency_id / quote_currency_id use standard FX terminology
-- (base/quote — e.g. USD/VES: USD is base, VES is quote). The pair
-- is generic, not hardcoded to USD/VES, so EUR/VES or USD/USDT rows
-- fit the same table without a schema change.
-- ============================================================

CREATE TABLE exchange_rates
(
    exchange_rates_id  BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),

    base_currency_id    BIGINT        NOT NULL REFERENCES currencies (currencies_id),
    quote_currency_id   BIGINT        NOT NULL REFERENCES currencies (currencies_id),

    -- Units of quote currency per 1 unit of base currency (e.g. 805.42 VES per 1 USD).
    rate                 NUMERIC(18, 8) NOT NULL CHECK (rate > 0),

    operation_date       DATE          NOT NULL,
    valid_from            TIMESTAMPTZ  NOT NULL,

    source                VARCHAR(30)  NOT NULL
        CONSTRAINT chk_exchange_rates_source CHECK (source IN ('BCV', 'EXCHANGE_RATES_API', 'MANUAL')),
    fetched_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    status                VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,

    CONSTRAINT chk_exchange_rates_currencies_distinct CHECK (base_currency_id <> quote_currency_id)
);

-- Idempotency for the ingestion job: one row per (pair, publish day). A
-- re-fetch of the same day upserts against this instead of duplicating.
CREATE UNIQUE INDEX uq_exchange_rates_pair_operation_date
    ON exchange_rates (base_currency_id, quote_currency_id, operation_date);

-- "Current rate" lookup: latest valid_from <= :at for a given pair.
CREATE INDEX idx_exchange_rates_pair_valid_from
    ON exchange_rates (base_currency_id, quote_currency_id, valid_from DESC);

CREATE TRIGGER trg_exchange_rates_updated_at
    BEFORE UPDATE ON exchange_rates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
