SET search_path TO app, public;

-- ============================================================
-- V100: registers `currency`, `exchange_rate` and `organization` in
-- entity_config (V60, renamed from audit_entity_config by V80) — their
-- *Service methods already carry @Auditable (CurrencyService since PR #235,
-- OrganizationService since PR #237), but DataChangeAuditAspect treats a
-- MISSING config row the same as `enabled=false` (fail-safe, spec
-- 07-audit.md §Decisión 6), so those annotations have been silent no-ops
-- until now. ExchangeRateService itself gets the missing @Auditable
-- annotations in this same change (its create/update/delete never had
-- them at all).
-- ============================================================

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('currency',      'Monedas',              'currencies'),
    ('exchange_rate', 'Tasas de cambio',      'exchange_rates'),
    ('organization',  'Organización',          'organizations')
ON CONFLICT (entity_key) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('currency'), ('exchange_rate'), ('organization')) AS want(entity_key)
        WHERE NOT EXISTS (SELECT 1 FROM entity_config c WHERE c.entity_key = want.entity_key)
    ) THEN
        RAISE EXCEPTION 'V100: one or more entity_config rows were not created';
    END IF;
END $$;
