SET search_path TO app, public;

-- V47: configurable billing cutover day (project chat 2026-08-07, vertical-8 Ítem B).
--
-- Anchor for the collection-commission engine's "scheduled collection date":
-- the day of the month a period's payment is considered due, independent of
-- the day the member actually enrolled. Default = day-of-month of enrolled_at
-- (behavior-preserving — existing memberships keep their current implicit
-- cutover), but editable so an advisor can move "inscrito el 3" to a
-- "cobro desde el 1" cadence.

ALTER TABLE memberships
    ADD COLUMN billing_start_day INT NULL
        CONSTRAINT chk_memberships_billing_start_day CHECK (billing_start_day BETWEEN 1 AND 28);

COMMENT ON COLUMN memberships.billing_start_day IS
    'Billing cutover day of month (1-28). NULL = derive from enrolled_at day-of-month. Anchors the collection-commission engine''s scheduled collection date.';

-- Back-fill existing rows from their enrollment day so the column reads
-- consistently even before the service starts setting it explicitly.
UPDATE memberships
SET billing_start_day = LEAST(EXTRACT(DAY FROM enrolled_at)::INT, 28)
WHERE billing_start_day IS NULL;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'memberships' AND column_name = 'billing_start_day'
    ) THEN
        RAISE EXCEPTION 'V47: memberships.billing_start_day was not created';
    END IF;

    IF EXISTS (SELECT 1 FROM memberships WHERE billing_start_day IS NULL) THEN
        RAISE EXCEPTION 'V47: back-fill left rows with billing_start_day NULL';
    END IF;
END $$;
