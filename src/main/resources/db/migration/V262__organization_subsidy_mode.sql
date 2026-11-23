SET search_path TO app, public;

-- ============================================================================
-- V262: per-organization subsidy mode and subsidy origin (hub ADR 0041 §1-§2).
--
-- 1. subsidies.origin — MANUAL (granted by a person through the admin screen)
--    or REFERRAL_REWARD (created by ReferrerRewardService, hub ADR 0018 §7).
--    The organization's subsidy mode only governs MANUAL subsidies; referral
--    rewards always apply and are controlled from the promotion.
--
--    Backfill heuristic for rows ReferrerRewardService already created: that
--    service never sets authorized_by and always writes the reason
--    "Recompensa por recomendar a <name> (promoción <name>)". Manual subsidies
--    always carry authorized_by (SubsidiesService requires the acting user),
--    so both conditions together identify the referral rewards without
--    touching any manual row.
--
-- 2. organizations.subsidy_mode — which fee types MANUAL subsidies discount:
--    DISABLED | MONTHLY | INSCRIPTION | MONTHLY_AND_INSCRIPTION.
--    Bootstrap: MONTHLY when there is at least one live manual subsidy with a
--    monthly percentage (today only the monthly fee is discounted), DISABLED
--    otherwise — nothing changes in production on deploy.
-- ============================================================================

ALTER TABLE subsidies
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD CONSTRAINT chk_subsidies_origin CHECK (origin IN ('MANUAL', 'REFERRAL_REWARD'));

COMMENT ON COLUMN subsidies.origin IS
    'Origen del subsidio: MANUAL (lo carga una persona) o REFERRAL_REWARD (recompensa por referido creada por el sistema). El modo de subsidios de la organización solo gobierna los MANUAL (ADR 0041)';

UPDATE subsidies
   SET origin = 'REFERRAL_REWARD'
 WHERE authorized_by IS NULL
   AND reason LIKE 'Recompensa por recomendar a %';

ALTER TABLE organizations
    ADD COLUMN subsidy_mode VARCHAR(30) NOT NULL DEFAULT 'DISABLED',
    ADD CONSTRAINT chk_organizations_subsidy_mode
        CHECK (subsidy_mode IN ('DISABLED', 'MONTHLY', 'INSCRIPTION', 'MONTHLY_AND_INSCRIPTION'));

COMMENT ON COLUMN organizations.subsidy_mode IS
    'Modo de subsidios manuales: DISABLED (inactivo), MONTHLY (mensualidad), INSCRIPTION (inscripción) o MONTHLY_AND_INSCRIPTION (ambas). Las recompensas por referido no dependen de este modo (ADR 0041)';

UPDATE organizations
   SET subsidy_mode = 'MONTHLY'
 WHERE EXISTS (
        SELECT 1
          FROM subsidies s
         WHERE s.is_active
           AND s.origin = 'MANUAL'
           AND s.monthly_percentage IS NOT NULL
           AND (s.valid_until IS NULL OR s.valid_until >= CURRENT_DATE));
