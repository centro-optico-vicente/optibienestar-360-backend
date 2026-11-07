SET search_path TO app, public;

-- ============================================================
-- V231: configurable onboarding policy (hub ADR 0037 §1).
--
-- One active row, same singleton shape as security_policies (V5/V191):
--   allow_member_self_registration  /affiliate self-registration on/off
--   allow_ally_self_registration    /ally self-registration on/off
--   member_approval_mode            MANUAL | AUTO_IF_PAYMENT_REPORTED | AUTO_ON_SUBMIT
--   member_activation_mode          ON_PAYMENT_APPROVED | ON_PAYMENT_REPORTED | IMMEDIATE
--   ally_approval_mode              MANUAL_WITH_AGREEMENT | AUTO_ON_SUBMIT
--   ally_publish_mode               MANUAL | ON_APPROVAL
--
-- The seeded row reproduces today's behavior: both self-registrations open,
-- staff approve every request, the membership waits for the approved
-- payment, allies need an operator + an ACTIVE agreement and are published
-- by hand.
--
-- Permissions: ONBOARDING_POLICY_VIEW / ONBOARDING_POLICY_UPDATE (one per
-- action; singleton, so no _CREATE/_DELETE), granted to SYSTEM and
-- ADMINISTRADOR like SECURITY_POLICY (V191).
-- ============================================================

CREATE TABLE onboarding_policies
(
    onboarding_policies_id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                            UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    allow_member_self_registration  BOOLEAN     NOT NULL DEFAULT TRUE,
    allow_ally_self_registration    BOOLEAN     NOT NULL DEFAULT TRUE,
    member_approval_mode            VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    member_activation_mode          VARCHAR(30) NOT NULL DEFAULT 'ON_PAYMENT_APPROVED',
    ally_approval_mode              VARCHAR(30) NOT NULL DEFAULT 'MANUAL_WITH_AGREEMENT',
    ally_publish_mode               VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    is_active                       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                      UUID,
    updated_by                      UUID,
    CONSTRAINT chk_onboarding_policies_member_approval_mode
        CHECK (member_approval_mode IN ('MANUAL', 'AUTO_IF_PAYMENT_REPORTED', 'AUTO_ON_SUBMIT')),
    CONSTRAINT chk_onboarding_policies_member_activation_mode
        CHECK (member_activation_mode IN ('ON_PAYMENT_APPROVED', 'ON_PAYMENT_REPORTED', 'IMMEDIATE')),
    CONSTRAINT chk_onboarding_policies_ally_approval_mode
        CHECK (ally_approval_mode IN ('MANUAL_WITH_AGREEMENT', 'AUTO_ON_SUBMIT')),
    CONSTRAINT chk_onboarding_policies_ally_publish_mode
        CHECK (ally_publish_mode IN ('MANUAL', 'ON_APPROVAL'))
);

COMMENT ON TABLE onboarding_policies IS
    'Single active row: which self-registrations are open and how requests are approved and activated (hub ADR 0037).';

CREATE TRIGGER trg_onboarding_policies_updated_at
    BEFORE UPDATE ON onboarding_policies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO onboarding_policies DEFAULT VALUES;

-- ─── Permissions ────────────────────────────────────────────────────────────
INSERT INTO permission_domains (code, name, icon, description, display_order)
SELECT 'ONBOARDING_POLICY', 'Política de registro', 'i-lucide-user-plus',
       'Auto-registro de afiliados y aliados, aprobación y activación',
       COALESCE(MAX(display_order), 0) + 1
FROM permission_domains
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('ONBOARDING_POLICY_VIEW',   'ONBOARDING_POLICY', 'Ver la política de registro'),
    ('ONBOARDING_POLICY_UPDATE', 'ONBOARDING_POLICY', 'Actualizar la política de registro')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('ONBOARDING_POLICY_VIEW', 'ONBOARDING_POLICY_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Audit registration ─────────────────────────────────────────────────────
INSERT INTO entity_config (entity_key, display_name, table_name)
VALUES ('onboarding_policy', 'Política de registro', 'onboarding_policies')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM onboarding_policies WHERE is_active) <> 1 THEN
        RAISE EXCEPTION 'V231: expected exactly one active onboarding policy';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES ('ONBOARDING_POLICY_VIEW'), ('ONBOARDING_POLICY_UPDATE')) AS want(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM permissions p
                     JOIN role_permissions rp ON rp.permission_id = p.permissions_id
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V231: ADMINISTRADOR did not receive the onboarding policy permissions';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES ('ONBOARDING_POLICY_VIEW'), ('ONBOARDING_POLICY_UPDATE')) AS want(name)
        WHERE NOT EXISTS (
            SELECT 1 FROM permissions p
                     JOIN role_permissions rp ON rp.permission_id = p.permissions_id
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V231: SYSTEM did not receive the onboarding policy permissions';
    END IF;
END $$;
