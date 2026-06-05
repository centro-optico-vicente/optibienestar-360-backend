-- ────────────────────────────────────────────────────────────────────────────
-- DEV-ONLY fix script — Allies & AllyServices publish flag (V11 amendment)
-- ────────────────────────────────────────────────────────────────────────────
-- Run this in DBeaver/DataGrip against your dev database to bring it in line
-- with the updated V11__allies.sql (which now includes `is_published` and
-- `published_at` columns on `allies` and `ally_services`).
--
-- This is needed because V11 was already applied in dev; editing the migration
-- file in place changes its checksum, so Flyway refuses to start until either:
--   (a) the in-place change is mirrored against the live schema (this script), OR
--   (b) the dev DB is wiped (`./gradlew flywayClean flywayMigrate`).
--
-- IDEMPOTENT — re-running is safe. ADD COLUMN IF NOT EXISTS / CREATE INDEX
-- IF NOT EXISTS skip silently when the change already landed.
--
-- After running this script, ALSO run:
--   ./gradlew flywayRepair
-- to recompute the V11 checksum so Flyway stops complaining.
-- ────────────────────────────────────────────────────────────────────────────

SET search_path TO app, public;

-- ─── allies ─────────────────────────────────────────────────────────────────
ALTER TABLE allies
    ADD COLUMN IF NOT EXISTS is_published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_allies_published
    ON allies (is_published)
    WHERE is_published;

-- ─── ally_services ──────────────────────────────────────────────────────────
ALTER TABLE ally_services
    ADD COLUMN IF NOT EXISTS is_published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

-- Consistency CHECK — drop-and-recreate so it's idempotent
ALTER TABLE ally_services
    DROP CONSTRAINT IF EXISTS chk_ally_services_published_requires_approved;
ALTER TABLE ally_services
    ADD CONSTRAINT chk_ally_services_published_requires_approved CHECK (
        is_published = FALSE OR review_status = 'APPROVED'
    );

CREATE INDEX IF NOT EXISTS idx_ally_services_published
    ON ally_services (ally_id)
    WHERE is_published AND review_status = 'APPROVED';

-- ─── Done ───────────────────────────────────────────────────────────────────
-- After running this, remember:  ./gradlew flywayRepair
-- (or delete and reapply: ./gradlew flywayClean flywayMigrate — if you don't
-- mind losing dev data).
