SET search_path TO app, public;

-- ============================================================================
-- V261: opt-in flag to publish the company identity (nombre comercial, razón
-- social, RIF) on GET /v1/public/organization. Contact data (address, email,
-- phones, WhatsApp, social networks) is always public; the identity stays
-- hidden (null) until an admin ticks the check. Defaults false: no change
-- until turned on.
-- ============================================================================

ALTER TABLE organizations
    ADD COLUMN public_identity_visible BOOLEAN NOT NULL DEFAULT false;
