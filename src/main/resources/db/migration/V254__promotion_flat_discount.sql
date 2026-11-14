SET search_path TO app, public;

-- ============================================================================
-- V254: a promotion discount can be a percentage or a fixed amount (hub ADR
-- 0038 §1, extends ADR 0018 / V175).
--
--   discount_type         PERCENTAGE | FLAT (default PERCENTAGE)
--   discount_pct          only for PERCENTAGE (now nullable)
--   discount_amount       only for FLAT: the amount off each discounted
--                         charge (inscription and/or monthly fee)
--   discount_currency_id  only for FLAT: the currency of discount_amount; the
--                         pricing converts it to the charge/payment currency
--                         at the charge/payment date (ADR 0015)
--
-- Backfill: every existing promotion becomes PERCENTAGE through the column
-- default and keeps its discount_pct, so the new coherence rule holds.
-- ============================================================================

ALTER TABLE promotions
    ADD COLUMN discount_type        VARCHAR(20)    NOT NULL DEFAULT 'PERCENTAGE',
    ADD COLUMN discount_amount      NUMERIC(18, 2),
    ADD COLUMN discount_currency_id BIGINT         REFERENCES currencies (currencies_id);

ALTER TABLE promotions ALTER COLUMN discount_pct DROP NOT NULL;

ALTER TABLE promotions DROP CONSTRAINT chk_promotions_discount_pct;

ALTER TABLE promotions
    ADD CONSTRAINT chk_promotions_discount_type CHECK (discount_type IN ('PERCENTAGE', 'FLAT')),
    -- PERCENTAGE ⇔ a % in (0, 100] and no amount; FLAT ⇔ an amount > 0 with its currency and no %.
    ADD CONSTRAINT chk_promotions_discount_coherence CHECK (
        (discount_type = 'PERCENTAGE' AND discount_pct IS NOT NULL AND discount_pct > 0 AND discount_pct <= 100
                                      AND discount_amount IS NULL AND discount_currency_id IS NULL) OR
        (discount_type = 'FLAT'       AND discount_amount IS NOT NULL AND discount_amount > 0
                                      AND discount_currency_id IS NOT NULL AND discount_pct IS NULL)
    );

COMMENT ON COLUMN promotions.discount_type IS 'Tipo de descuento: PERCENTAGE (porcentaje) o FLAT (monto fijo)';
COMMENT ON COLUMN promotions.discount_amount IS 'Monto fijo de descuento por cobro, en discount_currency_id; solo para FLAT';
COMMENT ON COLUMN promotions.discount_currency_id IS 'Moneda del monto fijo; solo para FLAT';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM promotions WHERE discount_type = 'PERCENTAGE' AND discount_pct IS NULL) THEN
        RAISE EXCEPTION 'V254: a PERCENTAGE promotion lost its discount_pct';
    END IF;
END $$;
