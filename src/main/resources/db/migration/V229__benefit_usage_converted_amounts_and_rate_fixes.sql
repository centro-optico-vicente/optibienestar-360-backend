SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V229: monto convertido en los consumos + ajustes del procesador de tasas.
--
-- 1. Regla general (ADR 0015): todo monto lleva su moneda y su monto
--    convertido a la moneda oficial de la organización. El consumo
--    (benefit_usages) guardaba copago y valor consumido con su moneda pero sin
--    conversión. Se agrega la foto de la conversión, tomada al registrar con la
--    tasa vigente en la FECHA DEL CONSUMO (usage_date; si es hoy, la del
--    instante del registro). Es una foto: nunca se recalcula, igual que
--    payments.exchange_rate_used.
--
-- 2. Backfill de los consumos existentes con la misma regla: la tasa vigente al
--    cierre del día del consumo (valid_from <= fin del día en Caracas), par
--    directo o inverso. Sin tasa vigente queda NULL (degradar, nunca bloquear).
--
-- 3. FETCH_EXCHANGE_RATES: con cron único a las 16:00 y 5 reintentos de 30 s,
--    toda publicación del BCV posterior a ~16:03 se perdía para siempre (el
--    upstream solo expone la última). En el servidor de pruebas faltan, entre
--    otras, las publicaciones del 10-09, 14-09, 08-10, 16-10, 19-10 y 23-10.
--    Ahora corre a las 07:00 (recupera lo publicado tarde el día hábil
--    anterior, antes de que entre en vigencia a las 08:00) y cada hora de
--    16:00 a 21:00. El runner ya no reporta fallo si lo último publicado ya
--    está guardado, así que las corridas extra no ensucian el historial.
-- ────────────────────────────────────────────────────────────────────────────

-- ─── 1. Foto de la conversión en el consumo ─────────────────────────────────
ALTER TABLE benefit_usages
    ADD COLUMN converted_currency_id         BIGINT REFERENCES currencies (currencies_id),
    ADD COLUMN consumption_amount_converted  NUMERIC(14, 2),
    ADD COLUMN consumption_exchange_rate     NUMERIC(18, 8),
    ADD COLUMN copay_amount_converted        NUMERIC(14, 2),
    ADD COLUMN copay_exchange_rate           NUMERIC(18, 8),
    ADD COLUMN exchange_rate_date            DATE;

ALTER TABLE benefit_usages
    ADD CONSTRAINT chk_benefit_usages_consumption_converted_paired
        CHECK ((consumption_amount_converted IS NULL) = (consumption_exchange_rate IS NULL)),
    ADD CONSTRAINT chk_benefit_usages_copay_converted_paired
        CHECK ((copay_amount_converted IS NULL) = (copay_exchange_rate IS NULL)),
    ADD CONSTRAINT chk_benefit_usages_converted_currency
        CHECK ((consumption_amount_converted IS NULL AND copay_amount_converted IS NULL)
               OR converted_currency_id IS NOT NULL);

COMMENT ON COLUMN benefit_usages.converted_currency_id IS
    'Moneda oficial de la organización al registrar el consumo; destino de la conversión (V229).';
COMMENT ON COLUMN benefit_usages.consumption_exchange_rate IS
    'Unidades de la moneda convertida por 1 unidad de consumption_currency, vigente en la fecha del consumo (V229).';
COMMENT ON COLUMN benefit_usages.copay_exchange_rate IS
    'Unidades de la moneda convertida por 1 unidad de copay_currency, vigente en la fecha del consumo (V229).';
COMMENT ON COLUMN benefit_usages.exchange_rate_date IS
    'Fecha de operación (publicación) de la tasa usada en la conversión (V229).';

-- ─── 2. Backfill ────────────────────────────────────────────────────────────
-- Rate (units of `official` per 1 unit of `src`) vigente at `at`: direct pair
-- first, inverse pair second — same order as CurrencyConversionService.convert.
CREATE OR REPLACE FUNCTION pg_temp.v229_rate(src BIGINT, official BIGINT, at TIMESTAMPTZ)
    RETURNS TABLE (rate NUMERIC, operation_date DATE)
    LANGUAGE sql STABLE AS
$$
    SELECT r.rate, r.operation_date
    FROM (
        SELECT er.rate, er.operation_date, er.valid_from, 1 AS pref
        FROM exchange_rates er
        WHERE er.base_currency_id = src AND er.quote_currency_id = official
          AND er.is_active AND er.valid_from <= at
        UNION ALL
        SELECT ROUND(1 / er.rate, 8), er.operation_date, er.valid_from, 2
        FROM exchange_rates er
        WHERE er.base_currency_id = official AND er.quote_currency_id = src
          AND er.is_active AND er.valid_from <= at
    ) r
    ORDER BY r.pref, r.valid_from DESC
    LIMIT 1
$$;

WITH org AS (
    SELECT official_currency_id FROM organizations ORDER BY organizations_id LIMIT 1
),
src AS (
    SELECT bu.benefit_usages_id,
           org.official_currency_id AS official,
           bu.consumption_amount, bu.consumption_currency_id,
           bu.copay_amount, bu.copay_currency_id,
           -- Rate vigente that day: end of the usage day in Caracas, never later than the registration itself.
           LEAST(((bu.usage_date + 1)::timestamp AT TIME ZONE 'America/Caracas') - INTERVAL '1 microsecond',
                 GREATEST(bu.usage_datetime, (bu.usage_date::timestamp AT TIME ZONE 'America/Caracas'))) AS at
    FROM benefit_usages bu
    CROSS JOIN org
    WHERE bu.consumption_amount IS NOT NULL OR bu.copay_amount IS NOT NULL
),
calc AS (
    SELECT s.benefit_usages_id, s.official,
           CASE WHEN s.consumption_currency_id = s.official THEN 1 ELSE cr.rate END AS c_rate,
           CASE WHEN s.consumption_currency_id = s.official THEN NULL ELSE cr.operation_date END AS c_date,
           CASE WHEN s.copay_currency_id = s.official THEN 1 ELSE pr.rate END AS p_rate,
           CASE WHEN s.copay_currency_id = s.official THEN NULL ELSE pr.operation_date END AS p_date,
           s.consumption_amount, s.copay_amount
    FROM src s
    LEFT JOIN LATERAL pg_temp.v229_rate(s.consumption_currency_id, s.official, s.at) cr
           ON s.consumption_amount IS NOT NULL AND s.consumption_currency_id <> s.official
    LEFT JOIN LATERAL pg_temp.v229_rate(s.copay_currency_id, s.official, s.at) pr
           ON s.copay_amount IS NOT NULL AND s.copay_currency_id <> s.official
)
UPDATE benefit_usages bu
SET consumption_exchange_rate    = CASE WHEN c.consumption_amount IS NULL THEN NULL ELSE c.c_rate END,
    consumption_amount_converted = CASE WHEN c.consumption_amount IS NULL OR c.c_rate IS NULL THEN NULL
                                        ELSE ROUND(c.consumption_amount * c.c_rate, 2) END,
    copay_exchange_rate          = CASE WHEN c.copay_amount IS NULL THEN NULL ELSE c.p_rate END,
    copay_amount_converted       = CASE WHEN c.copay_amount IS NULL OR c.p_rate IS NULL THEN NULL
                                        ELSE ROUND(c.copay_amount * c.p_rate, 2) END,
    exchange_rate_date           = COALESCE(c.c_date, c.p_date),
    converted_currency_id        = CASE WHEN (c.consumption_amount IS NOT NULL AND c.c_rate IS NOT NULL)
                                          OR (c.copay_amount IS NOT NULL AND c.p_rate IS NOT NULL)
                                        THEN c.official END
FROM calc c
WHERE bu.benefit_usages_id = c.benefit_usages_id;

-- ─── 3. Horario de FETCH_EXCHANGE_RATES ─────────────────────────────────────
UPDATE scheduled_jobs
SET cron_expression     = '0 0 7,16-21 * * MON-FRI',
    max_retry_attempts  = 3,
    retry_delay_seconds = 300
WHERE code = 'FETCH_EXCHANGE_RATES';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM scheduled_jobs
        WHERE code = 'FETCH_EXCHANGE_RATES' AND cron_expression = '0 0 7,16-21 * * MON-FRI'
    ) THEN
        RAISE EXCEPTION 'V229: FETCH_EXCHANGE_RATES schedule update did not apply (row seeded by V93 missing?)';
    END IF;
END $$;
