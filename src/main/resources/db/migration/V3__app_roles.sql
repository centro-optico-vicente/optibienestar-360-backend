-- ============================================================
-- DB roles — passwords injected via Flyway placeholders,
-- never hardcoded in version-controlled files.
-- ============================================================

-- ------------------------------------------------------------
-- optibienestar360_migration: DDL + DML — used by Flyway
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'optibienestar360_migration') THEN
        CREATE ROLE optibienestar360_migration WITH LOGIN PASSWORD '${migration_db_password}';
    END IF;
END $$;

DO $$ BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO optibienestar360_migration', current_database());
END $$;
GRANT USAGE, CREATE ON SCHEMA app TO optibienestar360_migration;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA app TO optibienestar360_migration;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA app TO optibienestar360_migration;


-- ------------------------------------------------------------
-- optibienestar360_app: DML only — used by the Spring Boot runtime
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'optibienestar360_app') THEN
        CREATE ROLE optibienestar360_app WITH LOGIN PASSWORD '${app_db_password}';
    END IF;
END $$;

DO $$ BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO optibienestar360_app', current_database());
END $$;
GRANT USAGE ON SCHEMA app TO optibienestar360_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA app TO optibienestar360_app;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA app TO optibienestar360_app;

-- Future tables — default privileges are scoped to the role that creates the
-- object. Two scenarios are covered:
--   * Flyway runs as optibienestar360_migration (recommended) → objects owned by it
--     → first ALTER block applies.
--   * Flyway runs as postgres (DATABASE_MIGRATION_USER fallback at fresh
--     bootstrap, before optibienestar360_migration exists) → objects owned by
--     postgres → second ALTER block applies, guarded by membership check
--     because non-postgres-member users (e.g. optibienestar360_migration) cannot
--     issue ALTER DEFAULT PRIVILEGES FOR ROLE postgres.
-- Without the FOR ROLE postgres block the runtime user would get
-- "permission denied for table users" on the first SELECT after a fresh
-- bootstrap with the default Flyway user.
ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO optibienestar360_app;
ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT USAGE, SELECT                  ON SEQUENCES TO optibienestar360_app;
DO $$
BEGIN
    IF pg_has_role(current_user, 'postgres', 'MEMBER') THEN
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO optibienestar360_app';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT USAGE, SELECT ON SEQUENCES TO optibienestar360_app';
    END IF;
END $$;


-- ------------------------------------------------------------
-- optibienestar360_readonly: SELECT only — reports, DBeaver, auditoría
-- ------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'optibienestar360_readonly') THEN
        CREATE ROLE optibienestar360_readonly WITH LOGIN PASSWORD '${readonly_db_password}';
    END IF;
END $$;

DO $$ BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO optibienestar360_readonly', current_database());
END $$;
GRANT USAGE ON SCHEMA app TO optibienestar360_readonly;
GRANT SELECT ON ALL TABLES    IN SCHEMA app TO optibienestar360_readonly;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA app TO optibienestar360_readonly;

ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT SELECT          ON TABLES    TO optibienestar360_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE optibienestar360_migration IN SCHEMA app GRANT USAGE, SELECT   ON SEQUENCES TO optibienestar360_readonly;
-- Same membership guard as the optibienestar360_app block above: only postgres-member
-- users can ALTER DEFAULT PRIVILEGES FOR ROLE postgres; skip when Flyway runs
-- as optibienestar360_migration (which is the recommended setup).
DO $$
BEGIN
    IF pg_has_role(current_user, 'postgres', 'MEMBER') THEN
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT SELECT ON TABLES TO optibienestar360_readonly';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA app GRANT USAGE, SELECT ON SEQUENCES TO optibienestar360_readonly';
    END IF;
END $$;


-- ------------------------------------------------------------
-- Schema hardening
-- ------------------------------------------------------------

-- Prevent any authenticated user from accessing schema app by default
REVOKE ALL ON SCHEMA app FROM PUBLIC;

-- Fix search_path per role — prevents search_path injection (CVE-2018-1058)
ALTER ROLE optibienestar360_migration SET search_path = app, public;
ALTER ROLE optibienestar360_app       SET search_path = app, public;
ALTER ROLE optibienestar360_readonly  SET search_path = app, public;

-- Per-role connection limits — caps each role so one cannot exhaust max_connections.
-- app must cover Hikari maximum-pool-size (20) × replicas (2) = 40, plus headroom.
-- Values are parameterized via Flyway placeholders (env-overridable, see application.properties).
ALTER ROLE optibienestar360_app       CONNECTION LIMIT ${app_conn_limit};
ALTER ROLE optibienestar360_migration CONNECTION LIMIT ${migration_conn_limit};
ALTER ROLE optibienestar360_readonly  CONNECTION LIMIT ${readonly_conn_limit};

-- Runtime timeouts for the app role — kills hung queries and abandoned open
-- transactions so a stuck request cannot hold a connection (and its locks) forever.
-- Only optibienestar360_app: migrations may legitimately run long; readonly is for ad-hoc tools.
-- Parameterized via Flyway placeholders (env-overridable, see application.properties).
ALTER ROLE optibienestar360_app SET statement_timeout = '${app_statement_timeout}';
ALTER ROLE optibienestar360_app SET idle_in_transaction_session_timeout = '${app_idle_in_transaction_timeout}';
