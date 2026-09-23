SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V152: closes the last gap in the commission-settlement frequency engine
-- (hub plan "commission-frequency-currency-unification") — seeds the
-- scheduled_jobs row that drives CollectionCommissionTierSettlementCutJobRunner.
-- V151 already automated CommissionTier (INSCRIPTION), HierarchyOverrideTier
-- (both INSCRIPTION and COLLECTION, generic by rule.category) and the
-- retroactive axis across all 4 rule entities. CollectionCommissionTier
-- (direct collection commission, AppliesTo.MONTHLY) was the only rule entity
-- still paid solely through the generic CommissionPayoutService sweep,
-- ignoring its own configurable cut frequency (V149).
--
-- Scheduled at 01:40 America/Caracas — before the 02:00/02:20/02:40 window
-- V151 seeded (COMMISSION_TIER_SETTLEMENT_CUT / HIERARCHY_OVERRIDE_SETTLEMENT_CUT
-- / COMMISSION_RETROACTIVE_SETTLEMENT_CUT), and clear of every other
-- scheduled job's slot documented there. No ordering dependency between this
-- job and the other three — collection-commission cuts don't re-price off
-- any of them — the earlier slot is purely to avoid a collision.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone)
VALUES (
    'COLLECTION_COMMISSION_TIER_SETTLEMENT_CUT',
    'Corte automático de comisiones de cobranza',
    'Recorre las reglas activas de collection_commission_tiers y, para cada una cuyo corte de liquidación parcial cierra hoy, liquida (marca PAID) las comisiones APPROVED de tipo MONTHLY de cada promotor con actividad en el corte. No re-precia: la comisión de cobranza se fija una sola vez por transacción al crearse (CommissionService.priceByCollectionSpeed), así que este corte es una transición de estado pura. Reutiliza CommissionPeriodicSettlementService.settleCollectionCut por promotor.',
    '0 40 1 * * *',
    'America/Caracas'
);

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM scheduled_jobs WHERE code = 'COLLECTION_COMMISSION_TIER_SETTLEMENT_CUT') <> 1 THEN
        RAISE EXCEPTION 'V152: COLLECTION_COMMISSION_TIER_SETTLEMENT_CUT scheduled_jobs row was not created';
    END IF;
END $$;
