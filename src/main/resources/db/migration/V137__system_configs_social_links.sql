SET search_path TO app, public;

-- ============================================================
-- V137: global social-media links shown across the public site
-- (landing footer/contact, etc.) — configurable from the admin
-- "Configuración del sistema" screen instead of hardcoded markup.
-- ============================================================

ALTER TABLE system_configs
    ADD COLUMN whatsapp  VARCHAR(30),
    ADD COLUMN instagram VARCHAR(255),
    ADD COLUMN facebook  VARCHAR(255);
