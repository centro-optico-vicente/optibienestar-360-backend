-- ============================================================
-- V31 — rename DB roles to the OptiBienestar 360 brand
-- optisalud_migration -> optibienestar360_migration
-- optisalud_app       -> optibienestar360_app
-- optisalud_readonly  -> optibienestar360_readonly
--
-- Roles were created by V3 (immutable). ALTER ROLE ... RENAME follows the role
-- OID, so every existing GRANT, object ownership, ALTER DEFAULT PRIVILEGES and
-- per-role setting (search_path, connection limit, timeouts) is preserved.
-- PostgreSQL 15 uses SCRAM-SHA-256, which does not embed the role name, so the
-- login password also survives the rename (this would NOT hold for MD5).
--
-- PRODUCTION NOTE: PostgreSQL forbids renaming the role of the current session,
-- and on an already-bootstrapped database Flyway connects AS the migration role.
-- This migration therefore CANNOT rename the migration role in place on prod,
-- and switching the datasource config to the new names before the role exists
-- would deadlock the next boot. Production renames the roles + database
-- out-of-band, as a superuser, in a maintenance window BEFORE deploying the new
-- config -- see scripts/ops/rename-db-to-optibienestar360.sql and
-- .ai/playbooks/rename-db-optibienestar360.md. After that runbook this migration
-- is a no-op (all guards skip). It still performs the rename automatically on
-- fresh / CI bootstraps, where Flyway runs as the postgres superuser.
-- ============================================================

DO $$
BEGIN
    IF EXISTS     (SELECT 1 FROM pg_roles WHERE rolname = 'optisalud_app')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_app') THEN
        ALTER ROLE optisalud_app RENAME TO optibienestar360_app;
    END IF;

    IF EXISTS     (SELECT 1 FROM pg_roles WHERE rolname = 'optisalud_readonly')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_readonly') THEN
        ALTER ROLE optisalud_readonly RENAME TO optibienestar360_readonly;
    END IF;

    -- Migration role: skip when connected as it (session user cannot be renamed).
    -- Fresh/CI bootstraps run as postgres, so this succeeds there; on prod it is
    -- a no-op and the ops runbook performs the rename.
    IF EXISTS     (SELECT 1 FROM pg_roles WHERE rolname = 'optisalud_migration')
       AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_migration')
       AND current_user <> 'optisalud_migration' THEN
        ALTER ROLE optisalud_migration RENAME TO optibienestar360_migration;
    END IF;
END $$;
