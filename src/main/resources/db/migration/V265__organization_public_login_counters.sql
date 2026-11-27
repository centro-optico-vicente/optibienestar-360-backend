SET search_path TO app, public;

-- ============================================================================
-- V265: configurable public counters on the login screen (hub ADR 0042).
-- Per-organization settings read by GET /v1/public/organization/stats:
--   * allies card: HIDDEN, PROFESSIONAL (segment B2P), COMMERCIAL (segment
--     B2B) or COMMERCIAL_AND_PROFESSIONAL (both), and the minimum count below
--     which the number is withheld (the card shows a generic text instead).
--   * members card: HIDDEN, MEMBERS (holders with an ACTIVE membership) or
--     MEMBERS_AND_BENEFICIARIES (plus their active beneficiaries), with its
--     own minimum.
-- Defaults: all allies shown from 20 (current behavior); members card shown
-- with holders + beneficiaries from 20. The threshold is enforced server-side, so a count below the
-- minimum never reaches the browser.
-- ============================================================================

ALTER TABLE organizations
    ADD COLUMN allies_counter_mode VARCHAR(32) NOT NULL DEFAULT 'COMMERCIAL_AND_PROFESSIONAL',
    ADD COLUMN allies_counter_min INTEGER NOT NULL DEFAULT 20,
    ADD COLUMN members_counter_mode VARCHAR(32) NOT NULL DEFAULT 'MEMBERS_AND_BENEFICIARIES',
    ADD COLUMN members_counter_min INTEGER NOT NULL DEFAULT 20,
    ADD CONSTRAINT ck_organizations_allies_counter_mode
        CHECK (allies_counter_mode IN ('HIDDEN', 'PROFESSIONAL', 'COMMERCIAL', 'COMMERCIAL_AND_PROFESSIONAL')),
    ADD CONSTRAINT ck_organizations_allies_counter_min CHECK (allies_counter_min >= 0),
    ADD CONSTRAINT ck_organizations_members_counter_min CHECK (members_counter_min >= 0),
    ADD CONSTRAINT ck_organizations_members_counter_mode
        CHECK (members_counter_mode IN ('HIDDEN', 'MEMBERS', 'MEMBERS_AND_BENEFICIARIES'));

COMMENT ON COLUMN organizations.allies_counter_mode IS
    'Tarjeta de aliados en el login (ADR 0042): HIDDEN (oculta), PROFESSIONAL (segmento B2P), COMMERCIAL (segmento B2B) o COMMERCIAL_AND_PROFESSIONAL (ambos)';
COMMENT ON COLUMN organizations.allies_counter_min IS
    'Cantidad mínima de aliados publicados para mostrar la cifra; por debajo, la tarjeta muestra un texto genérico';
COMMENT ON COLUMN organizations.members_counter_mode IS
    'Tarjeta de afiliados en el login: HIDDEN (oculta), MEMBERS (titulares activos) o MEMBERS_AND_BENEFICIARIES (titulares + beneficiarios activos)';
COMMENT ON COLUMN organizations.members_counter_min IS
    'Cantidad mínima de afiliados para mostrar la cifra; por debajo, la tarjeta muestra un texto genérico';
