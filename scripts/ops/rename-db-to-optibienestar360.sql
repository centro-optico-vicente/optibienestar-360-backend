-- ============================================================
-- Ops runbook (SQL) — OptiSalud -> OptiBienestar 360 DB cutover
--
-- RUN AS A SUPERUSER (postgres), WITH THE APP STOPPED, IN A MAINTENANCE WINDOW.
-- Idempotent: safe to re-run. Passwords are preserved (PostgreSQL 15 SCRAM).
--
-- Order of the full cutover (see .ai/playbooks/rename-db-optibienestar360.md):
--   1. Stop the app / both replicas (no active connections to the DB).
--   2. Run STEP 1 below (roles) — connected to any database.
--   3. Run STEP 2 below (database) — connected to the 'postgres' database.
--   4. Deploy the app with the new config (DATABASE_NAME=optibienestar360,
--      DATABASE_USER=optibienestar360_app,
--      DATABASE_MIGRATION_USER=optibienestar360_migration,
--      DATABASE_READONLY_USER=optibienestar360_readonly).
--   5. Flyway then runs V31 as a no-op and the app comes up on the new names.
-- ============================================================

-- STEP 1 — roles (run connected to any database) --------------
DO $$
BEGIN
    IF EXISTS     (SELECT 1 FROM pg_roles WHERE rolname = 'optisalud_migration')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_migration') THEN
        ALTER ROLE optisalud_migration RENAME TO optibienestar360_migration;
    END IF;
    IF EXISTS     (SELECT 1 FROM pg_roles WHERE rolname = 'optisalud_app')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_app') THEN
        ALTER ROLE optisalud_app RENAME TO optibienestar360_app;
    END IF;
    IF EXISTS     (SELECT 1 FROM pg_roles WHERE rolname = 'optisalud_readonly')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_readonly') THEN
        ALTER ROLE optisalud_readonly RENAME TO optibienestar360_readonly;
    END IF;
END $$;

-- STEP 2 — database (run connected to the 'postgres' database, NOT to the
-- database being renamed; ensure no other sessions are attached to it first:
--   SELECT pg_terminate_backend(pid) FROM pg_stat_activity
--     WHERE datname = 'optisalud' AND pid <> pg_backend_pid();
-- Then, only if the old database still exists:)
-- ALTER DATABASE optisalud RENAME TO optibienestar360;
