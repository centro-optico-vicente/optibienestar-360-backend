SET search_path TO app, public;

-- ============================================================================
-- V114: FX snapshot pair on commissions (ADR 0015).
--
-- Commission had no persisted exchange-rate snapshot at all — every read
-- live-converted `amount` to the org's official currency "as of now" via
-- ConversionEnricher (ADR 0015 §6 Caso B). That's correct for a still-PENDING
-- row, but it means the company has no record of the FX difference it
-- absorbs when a commission is devengada (earned_at) on one date and
-- actually disbursed at period close (paid_at) weeks later, with the rate
-- having moved in between — the promoter is always paid the correct amount
-- in their currency, but the org-currency cost of that payout can differ
-- from what was booked at devengo.
--
-- Two independent snapshots, both nullable:
--   exchange_rate_at_earned / earned_rate_date — rate vigente at earned_at,
--     set once at creation time (CommissionService).
--   exchange_rate_at_paid   / paid_rate_date   — rate vigente at paid_at,
--     set once when the row transitions to PAID (CommissionPayoutService).
--
-- The difference between the two (computed at read time, never persisted)
-- is the FX variance the company absorbed on that commission.
-- ============================================================================

ALTER TABLE commissions
    ADD COLUMN exchange_rate_at_earned NUMERIC(18, 8),
    ADD COLUMN earned_rate_date        DATE,
    ADD COLUMN exchange_rate_at_paid   NUMERIC(18, 8),
    ADD COLUMN paid_rate_date          DATE;
