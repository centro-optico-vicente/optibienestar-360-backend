SET search_path TO app, public;

-- ============================================================
-- V138: per-ally social links + Google Maps location, shown on
-- the public ally directory ficha alongside phone/website/address.
-- ============================================================

ALTER TABLE allies
    ADD COLUMN whatsapp        VARCHAR(30),
    ADD COLUMN instagram       VARCHAR(255),
    ADD COLUMN facebook        VARCHAR(255),
    ADD COLUMN google_maps_url VARCHAR(500);
