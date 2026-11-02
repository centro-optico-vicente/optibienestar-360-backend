-- ====================================================
-- V210__ally_product_flat_discount.sql
--
-- The preferential rate of an ally offer can be a fixed amount or a
-- percentage (hub ADR 0031 §8), the same FLAT / PERCENTAGE shape as the
-- commission bonus rewards (V37). The B2P agreement asks for the price the
-- affiliate pays, not only a percentage.
--
--   discount_type    FLAT | PERCENTAGE (default PERCENTAGE)
--   discount_amount  the amount off, in the offer's price currency
--                    (price_currency_id); only for FLAT.
--   discount_pct     unchanged; only for PERCENTAGE.
--
-- Backfill: every existing row becomes PERCENTAGE through the column default
-- and keeps its discount_pct as is. A NULL discount_pct (no discount agreed
-- yet) stays valid: PERCENTAGE only requires discount_amount to be NULL, so
-- "no discount" keeps meaning the full price. The final price stays derived
-- (AllyProduct.getFinalPrice), never stored.
-- ====================================================

ALTER TABLE ally_products
    ADD COLUMN discount_type   VARCHAR(20)   NOT NULL DEFAULT 'PERCENTAGE',
    ADD COLUMN discount_amount NUMERIC(10, 2);

ALTER TABLE ally_products
    ADD CONSTRAINT chk_ally_products_discount_type
        CHECK (discount_type IN ('FLAT', 'PERCENTAGE')),
    -- FLAT ⇔ an amount > 0 on a priced offer, no percentage;
    -- PERCENTAGE ⇔ no amount (the percentage itself may still be NULL).
    -- "Amount ≤ price" compares two columns of the same row but is enforced
    -- by the service, which answers a readable 422 instead of a 409.
    ADD CONSTRAINT chk_ally_products_discount_coherence CHECK (
        (discount_type = 'FLAT'       AND discount_amount IS NOT NULL AND discount_amount > 0
                                      AND discount_pct IS NULL AND price_amount IS NOT NULL) OR
        (discount_type = 'PERCENTAGE' AND discount_amount IS NULL)
    );

COMMENT ON COLUMN ally_products.discount_type IS 'Tarifa preferencial: FLAT (monto fijo en la moneda del precio) o PERCENTAGE (porcentaje)';
COMMENT ON COLUMN ally_products.discount_amount IS 'Monto de descuento en la moneda del precio (price_currency_id); solo para FLAT';
