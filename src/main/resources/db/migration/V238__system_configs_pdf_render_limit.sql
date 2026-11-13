SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V238: cap on generic PDF documents rendered at the same time, configurable
-- without redeploy. Each render holds the parsed fonts and the whole document
-- in memory; several in parallel multiply that peak inside the container's
-- memory limit (the backend was OOM-killed on 2026-11-11 while rendering a
-- PDF). A request that waits longer than pdf_render_wait_seconds for a free
-- slot gets 503 + Retry-After. 0 or -1 turns the respective limit off: no cap
-- on concurrent renders / wait for a free slot without a time limit.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE system_configs
	ADD COLUMN pdf_max_concurrent_renders INT NOT NULL DEFAULT 5
		CONSTRAINT chk_system_configs_pdf_max_concurrent_renders CHECK (pdf_max_concurrent_renders >= -1),
	ADD COLUMN pdf_render_wait_seconds INT NOT NULL DEFAULT 10
		CONSTRAINT chk_system_configs_pdf_render_wait_seconds CHECK (pdf_render_wait_seconds >= -1);

COMMENT ON COLUMN system_configs.pdf_max_concurrent_renders IS 'Max generic PDF documents rendered at the same time; 0 or -1 = no cap';
COMMENT ON COLUMN system_configs.pdf_render_wait_seconds IS 'Seconds a PDF request waits for a free render slot before 503; 0 or -1 = no time limit';
