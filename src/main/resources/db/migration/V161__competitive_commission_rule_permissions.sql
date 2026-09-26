SET search_path TO app, public;

-- ============================================================================
-- V161: permissions for the competitive commission rules CRUD (Fase 1, hub
-- plan competitive-commission-rules). Only the 4 RULE_* permissions land
-- here — COMPETITIVE_COMMISSION_AWARD_* and COMPETITIVE_COMMISSION_WINNER_DECIDE
-- (D16) are seeded in Fase 2, once the award/tie/decision tables they gate
-- actually exist.
--
-- Granted to any role that already holds the equivalent LEADERBOARD_PRIZE_*
-- permission (this feature eventually replaces the leaderboard, D9) and, for
-- VIEW_ALL only, to any role holding BONUS_RULE_VIEW_ALL — same "grant onto
-- an existing holder" pattern as V79 (ALLY_VIEW_ALL -> ALLY_USER_VIEW_ALL).
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COMPETITIVE_COMMISSION_RULE_VIEW_ALL', 'COMMISSIONS', 'Ver reglas de comisión competitivas'),
    ('COMPETITIVE_COMMISSION_RULE_CREATE',   'COMMISSIONS', 'Crear reglas de comisión competitivas'),
    ('COMPETITIVE_COMMISSION_RULE_UPDATE',   'COMMISSIONS', 'Editar reglas de comisión competitivas'),
    ('COMPETITIVE_COMMISSION_RULE_DELETE',   'COMMISSIONS', 'Eliminar reglas de comisión competitivas')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Roles with LEADERBOARD_PRIZE_{VIEW_ALL,CREATE,UPDATE,DELETE} get the 1:1 mapped new permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id
         JOIN (VALUES
             ('LEADERBOARD_PRIZE_VIEW_ALL', 'COMPETITIVE_COMMISSION_RULE_VIEW_ALL'),
             ('LEADERBOARD_PRIZE_CREATE',   'COMPETITIVE_COMMISSION_RULE_CREATE'),
             ('LEADERBOARD_PRIZE_UPDATE',   'COMPETITIVE_COMMISSION_RULE_UPDATE'),
             ('LEADERBOARD_PRIZE_DELETE',   'COMPETITIVE_COMMISSION_RULE_DELETE')
         ) AS mapping(old_name, new_name) ON mapping.old_name = p.name
         JOIN permissions np ON np.name = mapping.new_name
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Roles with BONUS_RULE_VIEW_ALL also get COMPETITIVE_COMMISSION_RULE_VIEW_ALL.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'BONUS_RULE_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name = 'COMPETITIVE_COMMISSION_RULE_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('COMPETITIVE_COMMISSION_RULE_VIEW_ALL'), ('COMPETITIVE_COMMISSION_RULE_CREATE'),
            ('COMPETITIVE_COMMISSION_RULE_UPDATE'),   ('COMPETITIVE_COMMISSION_RULE_DELETE')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V161: one or more competitive_commission_rule permissions were not created';
    END IF;
END $$;
