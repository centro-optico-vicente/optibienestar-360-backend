SET search_path TO app, public;

-- ============================================================================
-- V163: permissions for the competitive commission awards CRUD (Fase 2b, hub
-- plan competitive-commission-rules). COMPETITIVE_COMMISSION_WINNER_DECIDE
-- (D16) is seeded in Fase 2c, once the tie/manual-decision tables it gates
-- actually exist.
--
-- Granted to any role that already holds the equivalent LEADERBOARD_PRIZE_*
-- permission (this feature eventually replaces the leaderboard, D9) — same
-- "grant onto an existing holder" pattern as V161.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL', 'COMMISSIONS', 'Ver premios de comisión competitiva'),
    ('COMPETITIVE_COMMISSION_AWARD_PAY',      'COMMISSIONS', 'Pagar premios de comisión competitiva'),
    ('COMPETITIVE_COMMISSION_AWARD_VOID',     'COMMISSIONS', 'Anular premios de comisión competitiva')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

-- Roles with LEADERBOARD_PRIZE_VIEW_ALL get the 1:1 mapped new permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'LEADERBOARD_PRIZE_VIEW_ALL'
         CROSS JOIN permissions np
WHERE np.name = 'COMPETITIVE_COMMISSION_AWARD_VIEW_ALL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Roles with LEADERBOARD_PRIZE_PAY get PAY + VOID + (implicitly) VIEW_ALL.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'LEADERBOARD_PRIZE_PAY'
         CROSS JOIN permissions np
WHERE np.name IN ('COMPETITIVE_COMMISSION_AWARD_PAY', 'COMPETITIVE_COMMISSION_AWARD_VOID',
                   'COMPETITIVE_COMMISSION_AWARD_VIEW_ALL')
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ─────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('COMPETITIVE_COMMISSION_AWARD_VIEW_ALL'), ('COMPETITIVE_COMMISSION_AWARD_PAY'),
            ('COMPETITIVE_COMMISSION_AWARD_VOID')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V163: one or more competitive_commission_award permissions were not created';
    END IF;
END $$;
