SET search_path TO app, public;

-- ============================================================================
-- V125: two independent follow-ups to V124's campaigns schema.
--
--   1. Wires the 6 CAMPAIGN_* permissions V124 created into role_permissions.
--      Follows V121's pattern: SYSTEM/ADMINISTRADOR/OPERADOR get full ALL
--      scope (campaigns are a business-ops config surface, same tier as
--      commission tiers/bonus rules); OPERADOR_MEDICO is intentionally
--      excluded (no exposure to commission/incentive config today, matching
--      how CommissionTier/BonusRule permissions were scoped).
--
--   2. `description` columns on commission_tiers / hierarchy_override_tiers /
--      collection_commission_tiers (commission_bonus_rules already has one,
--      V37) — closes the doc-field gap flagged when anchoring these rule
--      tables to campaigns; a campaign-anchored tier benefits from a free-text
--      note the same way a bonus rule already does.
-- ============================================================================

-- ─── 1. CAMPAIGN_* role wiring ──────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR', 'OPERADOR')
  AND p.name IN ('CAMPAIGN_VIEW_ALL', 'CAMPAIGN_CREATE', 'CAMPAIGN_UPDATE', 'CAMPAIGN_DELETE',
                 'CAMPAIGN_EXCEPTION_CREATE', 'CAMPAIGN_EXCEPTION_DELETE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name LIKE 'CAMPAIGN_%'
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V125: SYSTEM did not receive every CAMPAIGN_* permission';
    END IF;
END $$;

-- ─── 2. description columns ─────────────────────────────────────────────────
ALTER TABLE commission_tiers ADD COLUMN description TEXT;
ALTER TABLE hierarchy_override_tiers ADD COLUMN description TEXT;
ALTER TABLE collection_commission_tiers ADD COLUMN description TEXT;
