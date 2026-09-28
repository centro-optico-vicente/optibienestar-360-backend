SET search_path TO app, public;

-- ============================================================================
-- V170: Fase 6 (hub plan competitive-commission-rules, post-prod) — drops the
-- legacy leaderboard tables now that V167 has migrated their data into
-- competitive_commission_rules/awards and every consuming Java class
-- (controllers, services, the job runner, DTOs) was already deleted in Fase 3
-- (PR #298). Confirmed zero remaining references anywhere in src/main/java.
--
-- Run explicitly with the user's authorization AFTER V167 was applied in
-- production, ahead of the real-data validation the hub plan's own
-- verification checklist calls for — kept as an explicit, separate PR per the
-- plan (never bundled with a feature change) precisely because it's
-- irreversible.
--
-- The disabled LEADERBOARD_PRIZE_AWARD scheduled_jobs row (V167, enabled =
-- false) is deliberately left in place — scheduled_job_runs has no ON DELETE
-- CASCADE back to scheduled_jobs, and its run history is worth keeping.
-- ============================================================================

DROP TABLE IF EXISTS leaderboard_prize_awards;
DROP TABLE IF EXISTS leaderboard_prizes;

-- role_permissions.permission_id → permissions has ON DELETE CASCADE (V30) —
-- role_permissions is its only referencing table, so this is self-cleaning.
DELETE FROM permissions
WHERE name IN (
    'LEADERBOARD_VIEW',
    'LEADERBOARD_PRIZE_MANAGE',
    'LEADERBOARD_PRIZE_VIEW_ALL',
    'LEADERBOARD_PRIZE_CREATE',
    'LEADERBOARD_PRIZE_UPDATE',
    'LEADERBOARD_PRIZE_DELETE'
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'app' AND tablename IN ('leaderboard_prizes', 'leaderboard_prize_awards')) THEN
        RAISE EXCEPTION 'V170: legacy leaderboard tables were not dropped';
    END IF;
    IF EXISTS (
        SELECT 1 FROM permissions
        WHERE name IN ('LEADERBOARD_VIEW', 'LEADERBOARD_PRIZE_MANAGE', 'LEADERBOARD_PRIZE_VIEW_ALL',
                       'LEADERBOARD_PRIZE_CREATE', 'LEADERBOARD_PRIZE_UPDATE', 'LEADERBOARD_PRIZE_DELETE')
    ) THEN
        RAISE EXCEPTION 'V170: legacy leaderboard permissions were not removed';
    END IF;
END $$;
