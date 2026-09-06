SET search_path TO app, public;

-- ============================================================
-- V96: countries.official_currency_id + full admin CRUD permissions
-- for Currency (new) and ExchangeRate PUT/DELETE (new) — see plan
-- "CRUD admin de Currency + ExchangeRate, y país↔moneda oficial".
--
-- countries.official_currency_id is nullable and seeded ONLY for
-- Venezuela (VE -> VES) — the other ~190 rows in the countries seed
-- are not used by any live flow today, so backfilling them all would
-- be speculative. This is metadata describing a country's own
-- official currency; it does NOT replace organizations.official_currency_id
-- (still the single-tenant conversion target for
-- ConversionEnricher/BonusAwardsService/LeaderboardPrizeService).
-- ============================================================

ALTER TABLE countries
    ADD COLUMN official_currency_id BIGINT REFERENCES currencies (currencies_id);

UPDATE countries
SET official_currency_id = (SELECT currencies_id FROM currencies WHERE code = 'VES')
WHERE iso_code = 'VE';


-- ─── New permissions: Currency admin CRUD (new module surface) ─────────────
-- Same 4-verb pattern as every other catalog (COUNTRY_*, GENDER_*, etc.) —
-- domain CURRENCY already exists (V92).
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('CURRENCY_VIEW_ALL', 'CURRENCY', 'Ver el catálogo de monedas'),
    ('CURRENCY_CREATE',   'CURRENCY', 'Crear una moneda'),
    ('CURRENCY_UPDATE',   'CURRENCY', 'Actualizar una moneda'),
    ('CURRENCY_DELETE',   'CURRENCY', 'Desactivar una moneda')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- ─── New permissions: ExchangeRate PUT/DELETE, scoped to source = MANUAL ───
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('EXCHANGE_RATE_UPDATE', 'CURRENCY', 'Corregir una tasa de cambio cargada manualmente'),
    ('EXCHANGE_RATE_DELETE', 'CURRENCY', 'Borrar una tasa de cambio cargada manualmente')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- SYSTEM / ADMINISTRADOR: all 6 new permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('CURRENCY_VIEW_ALL', 'CURRENCY_CREATE', 'CURRENCY_UPDATE', 'CURRENCY_DELETE',
                 'EXCHANGE_RATE_UPDATE', 'EXCHANGE_RATE_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM countries WHERE iso_code = 'VE' AND official_currency_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'V96: Venezuela did not end up with official_currency_id set';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('CURRENCY_VIEW_ALL'), ('CURRENCY_CREATE'), ('CURRENCY_UPDATE'), ('CURRENCY_DELETE'),
            ('EXCHANGE_RATE_UPDATE'), ('EXCHANGE_RATE_DELETE')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V96: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('CURRENCY_VIEW_ALL', 'CURRENCY_CREATE', 'CURRENCY_UPDATE', 'CURRENCY_DELETE',
                          'EXCHANGE_RATE_UPDATE', 'EXCHANGE_RATE_DELETE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V96: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('CURRENCY_VIEW_ALL', 'CURRENCY_CREATE', 'CURRENCY_UPDATE', 'CURRENCY_DELETE',
                          'EXCHANGE_RATE_UPDATE', 'EXCHANGE_RATE_DELETE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V96: ADMINISTRADOR did not receive the new permissions';
    END IF;
END $$;
