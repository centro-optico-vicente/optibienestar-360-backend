-- ────────────────────────────────────────────────────────────────────────────
-- V158 — benefit_usages: valor del consumo + categoría obligatoria
--
-- V24 solo guardaba `copay_amount`: lo que el afiliado puso de su bolsillo en
-- el mostrador. Eso NO es el valor de lo consumido, así que no se podía
-- responder "cuánto se consume en óptica vs farmacia" — la métrica que decide
-- en qué rubro invertir.
--
-- Dos cambios:
--
--   1. `consumption_amount` + `consumption_currency_id` — snapshot del valor
--      del consumo al registrarlo. Pareados por CHECK, mismo trato que el
--      copago (V24 + V87, que movió la moneda de VARCHAR(3) a FK).
--
--   2. `service_category_id` NOT NULL — `ally_service_id` es opcional (V24:44)
--      porque un consumo puede no mapear a un servicio del catálogo. Sin
--      categoría esos consumos quedaban fuera del reporte por rubro. Ahora la
--      categoría siempre viaja: se copia del servicio cuando lo hay, y si no,
--      el operador la elige.
--
-- Cubre los 2 primeros ítems del bloque D1 de `.ai/scope-additions-v3.md`
-- (fidelidad por consumo). Los programas de fidelidad y los vouchers siguen
-- pendientes; esto solo habilita el dato que ambos necesitan.
-- ────────────────────────────────────────────────────────────────────────────

SET search_path TO app, public;


-- ─── 1. Valor del consumo ───────────────────────────────────────────────────

ALTER TABLE benefit_usages
    ADD COLUMN consumption_amount      NUMERIC(10, 2),
    ADD COLUMN consumption_currency_id BIGINT REFERENCES currencies (currencies_id);

COMMENT ON COLUMN benefit_usages.consumption_amount IS
    'Valor del servicio consumido (precio de lista), distinto del copago que pagó el afiliado. NULL en los usos previos a V158.';

-- Ambos lados se mueven juntos, igual que el copago.
ALTER TABLE benefit_usages
    ADD CONSTRAINT chk_benefit_usages_consumption_paired CHECK (
        (consumption_amount IS NULL AND consumption_currency_id IS NULL)
        OR
        (consumption_amount IS NOT NULL AND consumption_amount >= 0 AND consumption_currency_id IS NOT NULL)
    );


-- ─── 2. Categoría del consumo ───────────────────────────────────────────────

-- Opción de respaldo para el consumo que no encaja en ninguna categoría del
-- catálogo. Existe porque la categoría pasa a ser obligatoria: sin ella el
-- operador quedaría bloqueado vendiendo algo fuera de catálogo. Idempotente
-- para no chocar con el UNIQUE de `code` si ya se sembró a mano.
INSERT INTO service_categories (code, name, description)
VALUES ('OTRO', 'Otro', 'Consumo que no corresponde a ninguna categoría del catálogo')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE benefit_usages
    ADD COLUMN service_category_id BIGINT REFERENCES service_categories (service_categories_id);

-- Backfill A: el consumo catalogado hereda la categoría de su servicio.
-- `ally_services.service_category_id` es NOT NULL (V11:138), así que esto
-- resuelve todas las filas con `ally_service_id`.
UPDATE benefit_usages bu
SET service_category_id = s.service_category_id
FROM ally_services s
WHERE s.ally_services_id = bu.ally_service_id
  AND bu.service_category_id IS NULL;

-- Backfill B: el consumo histórico sin servicio no tiene de dónde deducir la
-- categoría. Va a OTRO para poder cerrar el NOT NULL; queda identificable en
-- los reportes como "sin clasificar" y es un volumen que no vuelve a crecer.
UPDATE benefit_usages
SET service_category_id = (SELECT service_categories_id FROM service_categories WHERE code = 'OTRO')
WHERE service_category_id IS NULL;

ALTER TABLE benefit_usages
    ALTER COLUMN service_category_id SET NOT NULL;

COMMENT ON COLUMN benefit_usages.service_category_id IS
    'Rubro del consumo. Se copia del ally_service cuando lo hay; si no, lo elige el operador. Obligatorio para que todo consumo entre en el reporte por rubro.';


-- ─── 3. Índice de analítica ─────────────────────────────────────────────────

-- V24 ya indexa por (ally, fecha) y por (ally_service, fecha). Falta el eje que
-- pide el reporte por rubro: categoría + fecha, sobre filas vigentes.
CREATE INDEX idx_benefit_usages_category_date
    ON benefit_usages (service_category_id, usage_date DESC)
    WHERE is_active;
