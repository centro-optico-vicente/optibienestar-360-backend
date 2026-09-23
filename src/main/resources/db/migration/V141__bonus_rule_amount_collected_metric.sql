-- Part I (hub plan 2026-09-22): new AMOUNT_COLLECTED metric for
-- commission_bonus_rules — threshold_count keeps governing the two existing
-- count metrics (NEW_SUBSCRIBERS/ACTIVE_SUBSCRIBERS); threshold_amount +
-- threshold_currency_id are the new pair used only by AMOUNT_COLLECTED,
-- mutually exclusive with threshold_count (XOR CHECK below).

ALTER TABLE commission_bonus_rules
    DROP CONSTRAINT chk_bonus_rule_metric,
    ADD CONSTRAINT chk_bonus_rule_metric CHECK (metric IN ('NEW_SUBSCRIBERS', 'ACTIVE_SUBSCRIBERS', 'AMOUNT_COLLECTED'));

ALTER TABLE commission_bonus_rules
    ADD COLUMN threshold_amount     NUMERIC(14, 2),
    ADD COLUMN threshold_currency_id BIGINT REFERENCES currencies (currencies_id);

-- threshold_count already NOT NULL with a default (existing rows keep it);
-- the XOR is expressed as: AMOUNT_COLLECTED rows must have threshold_amount+
-- threshold_currency_id set and threshold_count = 0; every other metric must
-- have threshold_amount/threshold_currency_id both null.
ALTER TABLE commission_bonus_rules
    ADD CONSTRAINT commission_bonus_rules_threshold_xor CHECK (
        (metric = 'AMOUNT_COLLECTED' AND threshold_amount IS NOT NULL AND threshold_currency_id IS NOT NULL AND threshold_count = 0)
        OR
        (metric <> 'AMOUNT_COLLECTED' AND threshold_amount IS NULL AND threshold_currency_id IS NULL)
    );
