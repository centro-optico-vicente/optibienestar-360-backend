SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V238: cap on generic PDF documents rendered at the same time, configurable
-- without redeploy. Each render holds the parsed fonts and the whole document
-- in memory; several in parallel multiply that peak inside the container's
-- memory limit (the backend was OOM-killed on 2026-11-11 while rendering a
-- PDF). A request that waits longer than pdf_render_wait_seconds for a free
-- slot gets 503 + Retry-After.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE system_configs
	ADD COLUMN pdf_max_concurrent_renders INT NOT NULL DEFAULT 2
		CONSTRAINT chk_system_configs_pdf_max_concurrent_renders CHECK (pdf_max_concurrent_renders > 0),
	ADD COLUMN pdf_render_wait_seconds INT NOT NULL DEFAULT 30
		CONSTRAINT chk_system_configs_pdf_render_wait_seconds CHECK (pdf_render_wait_seconds >= 0);

COMMENT ON COLUMN system_configs.pdf_max_concurrent_renders IS 'Max generic PDF documents rendered at the same time';
COMMENT ON COLUMN system_configs.pdf_render_wait_seconds IS 'Seconds a PDF request waits for a free render slot before 503';
