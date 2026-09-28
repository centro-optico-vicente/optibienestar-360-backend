SET search_path TO app, public;

-- ============================================================================
-- V169: Fase 5 (hub plan competitive-commission-rules) — adds the two
-- OVERDUE_SETTLED_* metrics ("vencida saldada": a COVERED membership_charges
-- row whose covering payment landed, on the rule's own achievement date
-- basis, after its due_date — see MembershipCharge/OverdueSettledMetricProviders).
--
-- H10 caveat (carried from the Fase 0 freeze, hub plan §11 "Riesgos"):
-- membership_charges had zero OVERDUE/COVERED rows in every environment
-- checked so far — this metric's real-data shape is unvalidated. Verify
-- against production data before creating a live rule against it.
--
-- The constraint is found and dropped dynamically (never named on V160's
-- inline CHECK) rather than assuming Postgres's default naming, then
-- recreated with an explicit name so any future change to this list can
-- reference it directly.
-- ============================================================================

DO $$
DECLARE
    existing_check text;
BEGIN
    SELECT conname INTO existing_check
    FROM pg_constraint
    WHERE conrelid = 'competitive_commission_rules'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%metric%';

    IF existing_check IS NULL THEN
        RAISE EXCEPTION 'V169: could not find the metric CHECK constraint on competitive_commission_rules';
    END IF;

    EXECUTE format('ALTER TABLE competitive_commission_rules DROP CONSTRAINT %I', existing_check);
END $$;

ALTER TABLE competitive_commission_rules
    ADD CONSTRAINT competitive_commission_rules_metric_check
        CHECK (metric IN (
            'NEW_SUBSCRIBERS', 'ACTIVE_SUBSCRIBERS', 'SALES_COUNT', 'SALES_AMOUNT',
            'COLLECTION_COUNT', 'COLLECTION_AMOUNT', 'ADVANCE_COUNT', 'ADVANCE_AMOUNT',
            'COMMISSION_EARNED', 'OVERDUE_SETTLED_COUNT', 'OVERDUE_SETTLED_AMOUNT'
        ));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'competitive_commission_rules'::regclass
          AND conname = 'competitive_commission_rules_metric_check'
    ) THEN
        RAISE EXCEPTION 'V169: competitive_commission_rules_metric_check was not created';
    END IF;
END $$;
