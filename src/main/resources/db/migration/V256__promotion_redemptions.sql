SET search_path TO app, public;

-- ============================================================================
-- V256: every quota slot has a lifecycle (hub ADR 0038 §5-§6).
--
-- campaigns:
--   quota_trigger       when a reserved slot is confirmed: ON_ENROLLMENT (at
--                       once), ON_PAYMENT_REGISTERED (the inscription payment
--                       is registered) or ON_PAYMENT_CONFIRMED (it is
--                       approved, ADR 0008). A RECOVERY promotion has no
--                       inscription: its first monthly-fee payment confirms.
--   pending_ttl_days    days a PENDING slot waits for its confirmation; NULL =
--                       it never expires by itself.
--   usage_window        how long a CONFIRMED slot keeps its benefit:
--   usage_window_days   CAMPAIGN_DATES (until the campaign end, none if
--                       open-ended) or DAYS_AFTER_CONFIRMATION (N days).
--
-- promotion_redemptions: one row per membership promotion (its slot).
--   PENDING → CONFIRMED, or PENDING → EXPIRED (deadline passed) / RELEASED
--   (promotion lost before any discount). A CONFIRMED slot is RELEASED only
--   when no discount was ever applied.
--
-- Counters: campaigns/promotions.redemptions_count = PENDING + CONFIRMED
-- (occupied), confirmed_count = CONFIRMED.
--
-- Backfill: every existing membership promotion becomes a CONFIRMED slot
-- (reserved and confirmed at its assigned_at); the counters are recomputed
-- from the rows.
-- ============================================================================

-- ─── 1. campaigns: confirmation trigger, pending TTL, usage window ─────────
ALTER TABLE campaigns
    ADD COLUMN quota_trigger     VARCHAR(30) NOT NULL DEFAULT 'ON_ENROLLMENT',
    ADD COLUMN pending_ttl_days  INTEGER,
    ADD COLUMN usage_window      VARCHAR(30) NOT NULL DEFAULT 'CAMPAIGN_DATES',
    ADD COLUMN usage_window_days INTEGER;

ALTER TABLE campaigns
    ADD CONSTRAINT chk_campaigns_quota_trigger
        CHECK (quota_trigger IN ('ON_ENROLLMENT', 'ON_PAYMENT_REGISTERED', 'ON_PAYMENT_CONFIRMED')),
    ADD CONSTRAINT chk_campaigns_pending_ttl_days CHECK (pending_ttl_days IS NULL OR pending_ttl_days >= 1),
    ADD CONSTRAINT chk_campaigns_usage_window
        CHECK (usage_window IN ('CAMPAIGN_DATES', 'DAYS_AFTER_CONFIRMATION')),
    ADD CONSTRAINT chk_campaigns_usage_window_days
        CHECK ((usage_window_days IS NULL OR usage_window_days >= 1)
               AND (usage_window <> 'DAYS_AFTER_CONFIRMATION' OR usage_window_days IS NOT NULL));

-- ─── 2. promotion_redemptions ──────────────────────────────────────────────
CREATE TABLE promotion_redemptions
(
    promotion_redemptions_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                      UUID         NOT NULL UNIQUE,
    membership_promotion_id   BIGINT       NOT NULL UNIQUE
                                           REFERENCES membership_promotions (membership_promotions_id),
    promotion_id              BIGINT       NOT NULL REFERENCES promotions (promotions_id),
    campaign_id               BIGINT       NOT NULL REFERENCES campaigns (campaigns_id),

    redemption_status         VARCHAR(20)  NOT NULL,
    reserved_at               TIMESTAMPTZ  NOT NULL,
    confirmed_at              TIMESTAMPTZ,
    expires_at                TIMESTAMPTZ,
    usage_ends_at             TIMESTAMPTZ,
    released_at               TIMESTAMPTZ,
    confirmed_by_payment_id   BIGINT       REFERENCES payments (payments_id),

    is_active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    status                    VARCHAR(50),
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                UUID,
    updated_by                UUID,

    CONSTRAINT chk_promotion_redemptions_status
        CHECK (redemption_status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'RELEASED')),
    CONSTRAINT chk_promotion_redemptions_confirmed
        CHECK (redemption_status <> 'CONFIRMED' OR confirmed_at IS NOT NULL),
    CONSTRAINT chk_promotion_redemptions_released
        CHECK (redemption_status NOT IN ('EXPIRED', 'RELEASED') OR released_at IS NOT NULL)
);

CREATE TRIGGER trg_promotion_redemptions_updated_at
    BEFORE UPDATE ON promotion_redemptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_promotion_redemptions_campaign_status ON promotion_redemptions (campaign_id, redemption_status);
CREATE INDEX idx_promotion_redemptions_status_expires ON promotion_redemptions (redemption_status, expires_at);
CREATE INDEX idx_promotion_redemptions_promotion ON promotion_redemptions (promotion_id);

COMMENT ON TABLE promotion_redemptions IS 'Cupo de campaña ocupado por una promoción aplicada a una membresía (pendiente, confirmado, vencido o liberado)';

-- ─── 3. backfill: one CONFIRMED slot per existing membership promotion ─────
INSERT INTO promotion_redemptions (uuid, membership_promotion_id, promotion_id, campaign_id,
                                   redemption_status, reserved_at, confirmed_at, usage_ends_at)
SELECT gen_random_uuid(), mp.membership_promotions_id, mp.promotion_id, p.campaign_id,
       'CONFIRMED', mp.assigned_at, mp.assigned_at, c.ends_at
  FROM membership_promotions mp
  JOIN promotions p ON p.promotions_id = mp.promotion_id
  JOIN campaigns c ON c.campaigns_id = p.campaign_id;

UPDATE promotions p
   SET redemptions_count = COALESCE(s.occupied, 0),
       confirmed_count   = COALESCE(s.confirmed, 0)
  FROM (SELECT pr.promotions_id,
               COUNT(r.promotion_redemptions_id) FILTER (WHERE r.redemption_status IN ('PENDING', 'CONFIRMED')) AS occupied,
               COUNT(r.promotion_redemptions_id) FILTER (WHERE r.redemption_status = 'CONFIRMED') AS confirmed
          FROM promotions pr
          LEFT JOIN promotion_redemptions r ON r.promotion_id = pr.promotions_id
         GROUP BY pr.promotions_id) s
 WHERE s.promotions_id = p.promotions_id;

UPDATE campaigns c
   SET redemptions_count = COALESCE(s.occupied, 0),
       confirmed_count   = COALESCE(s.confirmed, 0)
  FROM (SELECT ca.campaigns_id,
               COUNT(r.promotion_redemptions_id) FILTER (WHERE r.redemption_status IN ('PENDING', 'CONFIRMED')) AS occupied,
               COUNT(r.promotion_redemptions_id) FILTER (WHERE r.redemption_status = 'CONFIRMED') AS confirmed
          FROM campaigns ca
          LEFT JOIN promotion_redemptions r ON r.campaign_id = ca.campaigns_id
         GROUP BY ca.campaigns_id) s
 WHERE s.campaigns_id = c.campaigns_id;

UPDATE scheduled_jobs
   SET description = 'Cierra las campañas cuya fecha de fin ya pasó, vence los cupos pendientes sin confirmar (liberando cupo y reactivando campañas agotadas) y termina las promociones cuya ventana de uso acabó.'
 WHERE code = 'CAMPAIGN_LIFECYCLE';

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM promotion_redemptions) <> (SELECT COUNT(*) FROM membership_promotions) THEN
        RAISE EXCEPTION 'V256: promotion_redemptions backfill incomplete';
    END IF;
END $$;
