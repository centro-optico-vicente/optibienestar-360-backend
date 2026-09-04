SET search_path TO app, public;

-- ============================================================
-- V88: payments — exchange-rate snapshot for conversions (ADR 0015 §5).
--
-- When a payment is registered in a currency different from what it
-- settles against (e.g. paid in VES against a plan priced in USD),
-- the rate applied at that moment is snapshotted here — never a live
-- JOIN against exchange_rates. A receipt from 3 months ago must keep
-- showing the rate that was vigente then, unaffected by later data.
--
-- Both columns are nullable: a payment registered in the same
-- currency it settles against never involved a conversion.
-- ============================================================

ALTER TABLE payments
    ADD COLUMN exchange_rate_used NUMERIC(18, 8) CHECK (exchange_rate_used IS NULL OR exchange_rate_used > 0),
    ADD COLUMN exchange_rate_date DATE;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_exchange_rate_paired CHECK (
        (exchange_rate_used IS NULL AND exchange_rate_date IS NULL)
        OR
        (exchange_rate_used IS NOT NULL AND exchange_rate_date IS NOT NULL)
    );
