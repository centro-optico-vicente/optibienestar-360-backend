-- ====================================================
-- V208__funeral_home_ally_type.sql
--
-- Adds the funeral home line of business requested by the client
-- (hub ADR 0031 §11, first step of the professional ally plan):
--
--   1. ally_types: FUNERARIA, a commercial (B2B) ally.
--   2. product_categories: SERVICIOS_FUNERARIOS, so funeral homes can
--      list their offers in the catalog.
--
-- ON CONFLICT without a target skips a row that collides on either
-- unique key (code OR name), in case an admin already created it by hand.
-- ====================================================

INSERT INTO ally_types (code, name, description, segment, professional_area) VALUES
    ('FUNERARIA', 'Funeraria', 'Servicios funerarios, velatorios y previsión exequial', 'B2B', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO product_categories (code, name, description) VALUES
    ('SERVICIOS_FUNERARIOS', 'Servicios funerarios', 'Velatorio, cremación, inhumación, traslados y previsión exequial')
ON CONFLICT DO NOTHING;
