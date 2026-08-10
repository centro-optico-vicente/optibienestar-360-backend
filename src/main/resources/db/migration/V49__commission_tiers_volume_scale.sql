SET search_path TO app, public;

-- V49: escalated commission by monthly inscription volume (vertical-8 Ítem A).
--
-- Replaces the v1 per-plan flat rates (INDIVIDUAL 20% / FAMILIAR 25% /
-- CORPORATIVO $5 flat, seeded V42 as plan-scoped BOTH tiers) with a single
-- unscoped scale by the promoter's monthly INSCRIPTION count: 0→25%, 41→30%,
-- 61/76/101→35%. The engine (CommissionService.selectTier) already applies
-- the highest tier the promoter's count qualifies for — only the seed changes.
--
-- The v1 tiers are NOT deleted (their commission_tier_id is snapshotted on
-- historical `commissions` rows) — they're narrowed from BOTH to MONTHLY-only
-- so they stop matching INSCRIPTION lookups but keep serving as the MONTHLY
-- volume-tier fallback for `CommissionService.priceByVolumeTier` (used when no
-- `collection_commission_tiers` bucket applies, V47/V48).

UPDATE commission_tiers
SET applies_to = 'MONTHLY'
WHERE applies_to = 'BOTH'
  AND plan_type IN ('INDIVIDUAL', 'FAMILIAR', 'CORPORATIVO')
  AND threshold_count = 0;

INSERT INTO commission_tiers (uuid, name, plan_type, threshold_count, commission_pct, flat_amount, period_strategy, applies_to)
VALUES
    (gen_random_uuid(), 'Inscripción 0-40/mes — 25%',   NULL, 0,   25.00, NULL, 'MONTHLY', 'INSCRIPTION'),
    (gen_random_uuid(), 'Inscripción 41-60/mes — 30%',  NULL, 41,  30.00, NULL, 'MONTHLY', 'INSCRIPTION'),
    (gen_random_uuid(), 'Inscripción 61-75/mes — 35%',  NULL, 61,  35.00, NULL, 'MONTHLY', 'INSCRIPTION'),
    (gen_random_uuid(), 'Inscripción 76-100/mes — 35%', NULL, 76,  35.00, NULL, 'MONTHLY', 'INSCRIPTION'),
    (gen_random_uuid(), 'Inscripción 101+/mes — 35%',   NULL, 101, 35.00, NULL, 'MONTHLY', 'INSCRIPTION');


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM commission_tiers WHERE plan_type IS NULL AND applies_to = 'INSCRIPTION') <> 5 THEN
        RAISE EXCEPTION 'V49: unscoped INSCRIPTION volume-scale tiers did not land';
    END IF;

    IF EXISTS (
        SELECT 1 FROM commission_tiers
        WHERE plan_type IN ('INDIVIDUAL', 'FAMILIAR', 'CORPORATIVO')
          AND threshold_count = 0
          AND applies_to <> 'MONTHLY'
    ) THEN
        RAISE EXCEPTION 'V49: v1 plan-scoped base tiers were not narrowed to MONTHLY-only';
    END IF;
END $$;
