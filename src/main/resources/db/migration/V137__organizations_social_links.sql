SET search_path TO app, public;

-- ============================================================
-- V137: global social-media links shown across the public site
-- (landing footer/contact, etc.) — configurable from the admin
-- "Organización" screen instead of hardcoded markup.
-- ============================================================

ALTER TABLE organizations
    ADD COLUMN whatsapp  VARCHAR(30),
    ADD COLUMN instagram VARCHAR(255),
    ADD COLUMN facebook  VARCHAR(255);
