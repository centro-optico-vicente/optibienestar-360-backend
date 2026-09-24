SET search_path TO app, public;

-- V154: multi-month advance payments (V153 plan). A single collection payment
-- can now cover several consecutive months: applied_period stays the first
-- covered month, coverage_through_period (nullable) is the last one. NULL
-- means single-month — the historical behavior is unchanged; no backfill
-- needed.
ALTER TABLE payments ADD COLUMN coverage_through_period DATE;
