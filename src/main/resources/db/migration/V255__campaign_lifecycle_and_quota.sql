SET search_path TO app, public;

-- ============================================================================
-- V255: campaign lifecycle, configurable closing and a two-level quota (hub
-- ADR 0038 §2-§4, amends ADR 0018 / V175).
--
-- campaigns:
--   ends_at                optional now: a campaign without an end date stays
--                          open until it is closed by quota or by hand.
--   lifecycle_status       DRAFT → ACTIVE ⇄ PAUSED, ACTIVE → SOLD_OUT → CLOSED.
--                          Only an ACTIVE campaign offers promotions.
--   close_on_end_date      the close-condition list (END_DATE / QUOTA_FILLED),
--   close_on_quota_filled  stored as booleans. Manual close is always possible;
--                          both false = closes only by hand.
--   max_redemptions        optional quota shared by every promotion of the
--                          campaign; redemptions_count = occupied slots,
--                          confirmed_count = confirmed slots (equal until
--                          V256 adds pending slots).
--   closed_*               when, why and who closed it (closed_by is NULL
--                          for an automatic close). closed_note is the
--                          optional free text of a manual close.
--   confirm_grace_until /  how long pending slots may still be confirmed after
--   confirm_grace_days     the close (a date or N days, never both).
--
-- promotions:
--   own_quota              the promotion counts its own max_redemptions on top
--                          of the campaign quota (backfilled TRUE where a cap
--                          already exists); without it the promotion only
--                          consumes from the campaign.
--   confirmed_count        confirmed slots of the promotion.
--
-- Backfill: campaigns whose ends_at already passed are CLOSED by END_DATE;
-- the rest are ACTIVE. Campaign counters start as the sum of their
-- promotions' counters.
-- ============================================================================

-- ─── 1. campaigns: optional end date ───────────────────────────────────────
ALTER TABLE campaigns ALTER COLUMN ends_at DROP NOT NULL;
ALTER TABLE campaigns DROP CONSTRAINT chk_campaigns_dates;
ALTER TABLE campaigns
    ADD CONSTRAINT chk_campaigns_dates CHECK (ends_at IS NULL OR ends_at > starts_at);

-- ─── 2. campaigns: lifecycle, close conditions, quota ──────────────────────
ALTER TABLE campaigns
    ADD COLUMN lifecycle_status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN close_on_end_date     BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN close_on_quota_filled BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN max_redemptions       INTEGER,
    ADD COLUMN redemptions_count     INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN confirmed_count       INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN closed_at             TIMESTAMPTZ,
    ADD COLUMN closed_reason         VARCHAR(20),
    ADD COLUMN closed_by_user_id     BIGINT       REFERENCES users (users_id),
    ADD COLUMN closed_note           TEXT,
    ADD COLUMN confirm_grace_until   TIMESTAMPTZ,
    ADD COLUMN confirm_grace_days    INTEGER;

UPDATE campaigns
   SET lifecycle_status = 'CLOSED',
       closed_reason    = 'END_DATE',
       closed_at        = ends_at
 WHERE ends_at < now();

UPDATE campaigns c
   SET redemptions_count = s.total,
       confirmed_count   = s.total
  FROM (SELECT campaign_id, SUM(redemptions_count) AS total
          FROM promotions
         GROUP BY campaign_id) s
 WHERE s.campaign_id = c.campaigns_id;

ALTER TABLE campaigns
    ADD CONSTRAINT chk_campaigns_lifecycle_status
        CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'SOLD_OUT', 'CLOSED')),
    ADD CONSTRAINT chk_campaigns_close_on_end_date
        CHECK (NOT close_on_end_date OR ends_at IS NOT NULL),
    ADD CONSTRAINT chk_campaigns_close_on_quota_filled
        CHECK (NOT close_on_quota_filled OR max_redemptions IS NOT NULL),
    ADD CONSTRAINT chk_campaigns_max_redemptions
        CHECK (max_redemptions IS NULL OR max_redemptions >= 1),
    ADD CONSTRAINT chk_campaigns_redemptions_count
        CHECK (redemptions_count >= 0 AND confirmed_count >= 0 AND confirmed_count <= redemptions_count),
    ADD CONSTRAINT chk_campaigns_closed_reason
        CHECK (closed_reason IS NULL OR closed_reason IN ('END_DATE', 'QUOTA_FILLED', 'MANUAL')),
    -- CLOSED ⇔ closed_at; a reason travels with every close.
    ADD CONSTRAINT chk_campaigns_closed
        CHECK ((lifecycle_status = 'CLOSED') = (closed_at IS NOT NULL)
               AND (closed_at IS NULL OR closed_reason IS NOT NULL)),
    ADD CONSTRAINT chk_campaigns_confirm_grace
        CHECK ((confirm_grace_days IS NULL OR confirm_grace_days >= 1)
               AND NOT (confirm_grace_until IS NOT NULL AND confirm_grace_days IS NOT NULL)),
    -- Rules scored or paid only at the end need an end (hub ADR 0038, consequences).
    ADD CONSTRAINT chk_campaigns_at_end_requires_ends_at
        CHECK (NOT (evaluate_only_at_end OR pay_only_at_end) OR ends_at IS NOT NULL);

CREATE INDEX idx_campaigns_lifecycle_status ON campaigns (lifecycle_status) WHERE is_active;

COMMENT ON COLUMN campaigns.lifecycle_status IS 'Ciclo de vida: DRAFT, ACTIVE, PAUSED, SOLD_OUT (agotada temporal) o CLOSED';
COMMENT ON COLUMN campaigns.max_redemptions IS 'Cupo total de la campaña, compartido por sus promociones; NULL = sin tope';
COMMENT ON COLUMN campaigns.redemptions_count IS 'Cupos ocupados (pendientes + confirmados)';
COMMENT ON COLUMN campaigns.confirmed_count IS 'Cupos confirmados';

-- ─── 3. promotions: own quota + confirmed counter ──────────────────────────
ALTER TABLE promotions
    ADD COLUMN own_quota       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN confirmed_count INTEGER NOT NULL DEFAULT 0;

UPDATE promotions SET own_quota = TRUE WHERE max_redemptions IS NOT NULL;
UPDATE promotions SET confirmed_count = redemptions_count;

ALTER TABLE promotions
    ADD CONSTRAINT chk_promotions_own_quota CHECK (own_quota OR max_redemptions IS NULL),
    ADD CONSTRAINT chk_promotions_confirmed_count
        CHECK (confirmed_count >= 0 AND confirmed_count <= redemptions_count);

COMMENT ON COLUMN promotions.own_quota IS 'La promoción cuenta su propio cupo (max_redemptions) además del cupo de la campaña';

-- ─── 4. permissions (CAMPAIGN domain) ──────────────────────────────────────
INSERT INTO permissions (name, domain_id, description)
SELECT v.name, d.permission_domains_id, v.description
FROM (VALUES
    ('CAMPAIGN_CLOSE',  'Cerrar una campaña a mano'),
    ('CAMPAIGN_REOPEN', 'Reabrir una campaña cerrada ampliando su cupo o su fecha'),
    ('CAMPAIGN_PAUSE',  'Pausar y reanudar una campaña')
) AS v(name, description)
CROSS JOIN permission_domains d
WHERE d.code = 'CAMPAIGN'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('CAMPAIGN_CLOSE', 'CAMPAIGN_REOPEN', 'CAMPAIGN_PAUSE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── 5. CAMPAIGN_LIFECYCLE daily job ───────────────────────────────────────
INSERT INTO scheduled_jobs (code, display_name, description, cron_expression, timezone, parameters)
VALUES (
    'CAMPAIGN_LIFECYCLE',
    'Ciclo de vida de campañas',
    'Cierra las campañas cuya fecha de fin ya pasó (si la fecha es una condición de cierre) y mantiene al día el estado de sus cupos.',
    '0 10 0 * * *',
    'America/Caracas',
    '{}'::jsonb
)
ON CONFLICT (code) DO NOTHING;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM permissions WHERE name IN ('CAMPAIGN_CLOSE', 'CAMPAIGN_REOPEN', 'CAMPAIGN_PAUSE')) < 3 THEN
        RAISE EXCEPTION 'V255: CAMPAIGN_CLOSE/REOPEN/PAUSE permissions were not created';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM scheduled_jobs WHERE code = 'CAMPAIGN_LIFECYCLE') THEN
        RAISE EXCEPTION 'V255: CAMPAIGN_LIFECYCLE was not seeded';
    END IF;
END $$;
