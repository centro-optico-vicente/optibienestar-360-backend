SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V239: new defaults for the PDF render cap added in V238 — 5 renders at the
-- same time and a 10-second wait for a free slot (V238 shipped 2 / 30). V238 is
-- not edited because it may already be applied (Flyway checksum).
-- The singleton row moves to the new defaults only where it still holds the
-- old ones, so a value an admin already changed is kept.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE system_configs
	ALTER COLUMN pdf_max_concurrent_renders SET DEFAULT 5,
	ALTER COLUMN pdf_render_wait_seconds SET DEFAULT 10;

UPDATE system_configs SET pdf_max_concurrent_renders = 5 WHERE pdf_max_concurrent_renders = 2;
UPDATE system_configs SET pdf_render_wait_seconds = 10 WHERE pdf_render_wait_seconds = 30;
