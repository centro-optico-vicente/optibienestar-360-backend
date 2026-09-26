SET search_path TO app, public;

-- ============================================================================
-- V166: COMPETITIVE_COMMISSION_WINNER_DECIDE (D16, Fase 2c) — resolve a tie,
-- redirect or disqualify a winner, and revert a decision. Granted to entry to
-- any role that already holds COMPETITIVE_COMMISSION_AWARD_PAY (the
-- coordinator role), and stays independently assignable from the role editor.
-- ============================================================================

INSERT INTO permissions (name, domain_id, description)
SELECT 'COMPETITIVE_COMMISSION_WINNER_DECIDE', pd.permission_domains_id,
       'Resolver empates y decidir ganadores de comisión competitiva'
FROM permission_domains pd WHERE pd.code = 'COMMISSIONS'
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.permissions_id
FROM role_permissions rp
         JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = 'COMPETITIVE_COMMISSION_AWARD_PAY'
         CROSS JOIN permissions np
WHERE np.name = 'COMPETITIVE_COMMISSION_WINNER_DECIDE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'COMPETITIVE_COMMISSION_WINNER_DECIDE') THEN
        RAISE EXCEPTION 'V166: COMPETITIVE_COMMISSION_WINNER_DECIDE was not created';
    END IF;
END $$;
