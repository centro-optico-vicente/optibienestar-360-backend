-- ============================================================================
-- V246 — Snapshotted rate dates switch from operation_date to the value date
-- ============================================================================
-- Until now every conversion snapshot stored the rate's operation_date (BCV
-- publish day) as its "rate date". BCV, its website and every public source
-- identify a rate by its "Fecha Valor" (the day it becomes vigente, 08:00
-- America/Caracas), so the app showed one business day earlier than BCV —
-- and over a weekend, the previous Thursday for the rate ruling on Saturday.
-- CurrencyConversionService now returns the value date (valid_from's local
-- date); this migration rewrites the dates already stored so each column keeps
-- a single meaning.
--
-- Mapping: operation_date -> valid_from's date in America/Caracas, only for
-- operation dates whose rows all agree on that value date (BCV/API rows of the
-- same day share it; a MANUAL row with a different vigency makes the day
-- ambiguous and it is left untouched). Rows with no rate or rate = 1 are
-- same-currency/no-conversion snapshots whose date is not an operation_date.
-- ============================================================================

CREATE TEMP TABLE v246_rate_date_map ON COMMIT DROP AS
SELECT operation_date,
       MIN((valid_from AT TIME ZONE 'America/Caracas')::date) AS value_date
FROM exchange_rates
GROUP BY operation_date
HAVING COUNT(DISTINCT (valid_from AT TIME ZONE 'America/Caracas')::date) = 1;

UPDATE payments t SET exchange_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.exchange_rate_date = m.operation_date
  AND t.exchange_rate_used IS NOT NULL AND t.exchange_rate_used <> 1;

UPDATE person_credit_entries t SET exchange_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.exchange_rate_date = m.operation_date
  AND t.exchange_rate_used IS NOT NULL AND t.exchange_rate_used <> 1;

UPDATE promoter_bonus_awards t SET exchange_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.exchange_rate_date = m.operation_date
  AND t.exchange_rate_used IS NOT NULL AND t.exchange_rate_used <> 1;

UPDATE benefit_usages t SET exchange_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.exchange_rate_date = m.operation_date
  AND COALESCE(t.consumption_exchange_rate, t.copay_exchange_rate) IS NOT NULL
  AND COALESCE(t.consumption_exchange_rate, t.copay_exchange_rate) <> 1;

UPDATE commissions t SET earned_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.earned_rate_date = m.operation_date
  AND t.exchange_rate_at_earned IS NOT NULL AND t.exchange_rate_at_earned <> 1;

UPDATE commissions t SET paid_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.paid_rate_date = m.operation_date
  AND t.exchange_rate_at_paid IS NOT NULL AND t.exchange_rate_at_paid <> 1;

UPDATE competitive_commission_award_settlements t SET paid_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.paid_rate_date = m.operation_date
  AND t.exchange_rate_at_paid IS NOT NULL AND t.exchange_rate_at_paid <> 1;

UPDATE competitive_commission_awards t SET award_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.award_rate_date = m.operation_date
  AND t.exchange_rate_at_award IS NOT NULL AND t.exchange_rate_at_award <> 1;

UPDATE competitive_commission_awards t SET paid_rate_date = m.value_date
FROM v246_rate_date_map m
WHERE t.paid_rate_date = m.operation_date
  AND t.exchange_rate_at_paid IS NOT NULL AND t.exchange_rate_at_paid <> 1;

COMMENT ON COLUMN benefit_usages.exchange_rate_date IS
    'Fecha valor (día de vigencia BCV) de la tasa usada en la conversión (V229, V246).';
