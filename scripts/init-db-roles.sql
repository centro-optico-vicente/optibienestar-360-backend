-- ────────────────────────────────────────────────────────────────────────────
-- Initial DB roles for OptiBienestar 360 — run once as a SUPERUSER (postgres)
-- on a freshly (re)created database, before pointing Flyway at the migration
-- role. Idempotent (safe to re-run): each role is created only if missing.
-- ────────────────────────────────────────────────────────────────────────────
-- Recreate-from-scratch flow (no prod data to preserve):
--   1. As postgres:  DROP DATABASE IF EXISTS optibienestar360;
--                     CREATE DATABASE optibienestar360;
--   2. Run this script against the new database to create the three roles.
--   3. Start the backend — Flyway (as optibienestar360_migration, or postgres
--      via DATABASE_MIGRATION_USER fallback) runs the migrations; V3 is
--      idempotent (IF NOT EXISTS) and grants the schema/table privileges once
--      the `app` schema exists, so it will not clash with the roles created here.
--
-- Only role-level attributes live here (creation, password, search_path,
-- connection limits, timeouts). Schema/table GRANTs stay in V3 because they
-- depend on the `app` schema, which earlier migrations create.
--
-- Passwords: edit the SET LOCAL block below to match your deployment secrets
-- (DATABASE_MIGRATION_PASSWORD / DATABASE_PASSWORD / DATABASE_READONLY_PASSWORD).
-- SET LOCAL scopes them to this transaction (discarded on COMMIT). PostgreSQL 15
-- stores SCRAM-SHA-256 hashes, so the plaintext never persists.
-- ────────────────────────────────────────────────────────────────────────────

BEGIN;

-- ── Passwords (edit here) ───────────────────────────────────────────────────
SET LOCAL app.migration_password = 'changeme-dev';
SET LOCAL app.app_password       = 'changeme-dev';
SET LOCAL app.readonly_password  = 'changeme-dev';

-- ── Roles (created only if missing) ─────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_migration') THEN
        EXECUTE format('CREATE ROLE optibienestar360_migration WITH LOGIN PASSWORD %L',
                       current_setting('app.migration_password'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_app') THEN
        EXECUTE format('CREATE ROLE optibienestar360_app WITH LOGIN PASSWORD %L',
                       current_setting('app.app_password'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'optibienestar360_readonly') THEN
        EXECUTE format('CREATE ROLE optibienestar360_readonly WITH LOGIN PASSWORD %L',
                       current_setting('app.readonly_password'));
    END IF;
END $$;

-- ── Role hardening (mirrors V3 defaults; all idempotent) ────────────────────
-- Pin search_path per role — prevents search_path injection (CVE-2018-1058).
ALTER ROLE optibienestar360_migration SET search_path = app, public;
ALTER ROLE optibienestar360_app       SET search_path = app, public;
ALTER ROLE optibienestar360_readonly  SET search_path = app, public;

-- Per-role connection limits (app covers Hikari pool 20 × 2 replicas + headroom).
ALTER ROLE optibienestar360_app       CONNECTION LIMIT 50;
ALTER ROLE optibienestar360_migration CONNECTION LIMIT 5;
ALTER ROLE optibienestar360_readonly  CONNECTION LIMIT 10;

-- Runtime timeouts for the app role — kill hung queries / abandoned txns.
ALTER ROLE optibienestar360_app SET statement_timeout = '30s';
ALTER ROLE optibienestar360_app SET idle_in_transaction_session_timeout = '60s';

COMMIT;
