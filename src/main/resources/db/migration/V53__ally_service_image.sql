SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V53: ally_services.image_key — public catalog image for a published service.
--
-- Only the R2 key is persisted, not the full URL: the mapper derives
-- `publicBaseUrl + image_key` at read time, so a future change of public
-- domain doesn't require a data migration. Uses the PUBLIC bucket (spec
-- 08-storage-r2.md §1), key layout "public/services/{ally_services.uuid}/...".
-- Nullable — a service can be published without an image.
-- ────────────────────────────────────────────────────────────────────────────

ALTER TABLE ally_services
    ADD COLUMN image_key VARCHAR(500);
