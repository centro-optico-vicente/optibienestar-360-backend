SET search_path TO app, public;

-- ============================================================
-- V91: ally_services.price_usd hardcoded the currency in the
-- column name itself and had no FK (ADR 0015 follow-up). Renamed
-- to price_amount + paired with price_currency_id (both nullable,
-- like the original — not every service shows a reference price).
--
-- Not wired into any automatic conversion today: benefit_usages
-- copay is entered manually by the ally operator at the counter
-- (BenefitUsageRegisterRequest), not derived from this price. This
-- migration only makes the existing reference price honest about
-- its currency — auto-suggesting a converted copay from it is
-- future work.
-- ============================================================

ALTER TABLE ally_services RENAME COLUMN price_usd TO price_amount;

ALTER TABLE ally_services ADD COLUMN price_currency_id BIGINT REFERENCES currencies (currencies_id);

UPDATE ally_services
SET price_currency_id = (SELECT currencies_id FROM currencies WHERE code = 'USD')
WHERE price_amount IS NOT NULL;

ALTER TABLE ally_services
    ADD CONSTRAINT chk_ally_services_price_currency_paired CHECK (
        (price_amount IS NULL AND price_currency_id IS NULL)
        OR
        (price_amount IS NOT NULL AND price_currency_id IS NOT NULL)
    );
