SET search_path TO app, public;

-- ============================================================
-- V92: promoter_bonus_awards / leaderboard_prize_awards — exchange-rate
-- snapshot for conversions at payout time (ADR 0015 §5/§7 Caso A).
--
-- Mirrors V88 (payments): the rate applied at the moment a bonus/prize is
-- actually PAID is snapshotted here — never a live JOIN against
-- exchange_rates — so a receipt keeps showing the rate that was vigente
-- then, unaffected by later data.
--
-- leaderboard_prize_awards had no payout timestamp at all until now (only
-- awarded_at, which is period-close, not the payout moment) — paid_at is
-- added alongside the snapshot columns so PAID has an anchor, same as
-- promoter_bonus_awards already had (V37).
--
-- Both snapshot columns are nullable and paired: an award paid in the same
-- currency it is denominated in never involved a conversion.
-- ============================================================

ALTER TABLE promoter_bonus_awards
    ADD COLUMN exchange_rate_used NUMERIC(18, 8) CHECK (exchange_rate_used IS NULL OR exchange_rate_used > 0),
    ADD COLUMN exchange_rate_date DATE;

ALTER TABLE promoter_bonus_awards
    ADD CONSTRAINT chk_bonus_awards_exchange_rate_paired CHECK (
        (exchange_rate_used IS NULL AND exchange_rate_date IS NULL)
        OR
        (exchange_rate_used IS NOT NULL AND exchange_rate_date IS NOT NULL)
    );

ALTER TABLE leaderboard_prize_awards
    ADD COLUMN paid_at             TIMESTAMPTZ,
    ADD COLUMN payout_reference    VARCHAR(120),
    ADD COLUMN exchange_rate_used  NUMERIC(18, 8) CHECK (exchange_rate_used IS NULL OR exchange_rate_used > 0),
    ADD COLUMN exchange_rate_date  DATE;

ALTER TABLE leaderboard_prize_awards
    ADD CONSTRAINT chk_leaderboard_prize_awards_exchange_rate_paired CHECK (
        (exchange_rate_used IS NULL AND exchange_rate_date IS NULL)
        OR
        (exchange_rate_used IS NOT NULL AND exchange_rate_date IS NOT NULL)
    );


-- ─── New permissions: mark-as-paid workflow (missing until now) ─────────────
-- BONUS_AWARD_PAY: PromoterBonusAward has no admin write path today
-- (AdminBonusAwardController is read-only) — this is the first mutation.
-- LEADERBOARD_PRIZE_PAY: distinct from LEADERBOARD_PRIZE_CREATE (which
-- already gates /award, i.e. granting) — paying is a separate lifecycle
-- action, same split COMMISSION_PAYOUT already has from commission creation.
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('BONUS_AWARD_PAY',      'COMMISSIONS', 'Marcar un bono/premio de promotor otorgado como pagado'),
    ('LEADERBOARD_PRIZE_PAY', 'COMMISSIONS', 'Marcar un premio de ranking otorgado como pagado')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ─── New domain + permissions: admin exchange-rate endpoints (ADR 0015 §7) ──
-- No CRUD surface existed at all over exchange_rates until now (only the
-- ingestion job, not yet built, was ever going to write it) — this is the
-- stop-gap manual entry point so the conversion service has something to
-- read in any environment before FetchExchangeRatesJob ships (Tarea 2.13).
INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('CURRENCY', 'Monedas y tasas de cambio', 'i-lucide-banknote',
        'Maestro de monedas y tasas de cambio (ADR 0015)', 120)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('EXCHANGE_RATE_VIEW_ALL', 'CURRENCY', 'Ver el historial de tasas de cambio'),
    ('EXCHANGE_RATE_CREATE',   'CURRENCY', 'Registrar una tasa de cambio manualmente')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- SYSTEM / ADMINISTRADOR: both new pay actions + the new exchange-rate permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('BONUS_AWARD_PAY', 'LEADERBOARD_PRIZE_PAY', 'EXCHANGE_RATE_VIEW_ALL', 'EXCHANGE_RATE_CREATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('BONUS_AWARD_PAY'), ('LEADERBOARD_PRIZE_PAY'),
                               ('EXCHANGE_RATE_VIEW_ALL'), ('EXCHANGE_RATE_CREATE')) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V92: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('BONUS_AWARD_PAY', 'LEADERBOARD_PRIZE_PAY', 'EXCHANGE_RATE_VIEW_ALL', 'EXCHANGE_RATE_CREATE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V92: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('BONUS_AWARD_PAY', 'LEADERBOARD_PRIZE_PAY', 'EXCHANGE_RATE_VIEW_ALL', 'EXCHANGE_RATE_CREATE')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V92: ADMINISTRADOR did not receive the new permissions';
    END IF;
END $$;
