SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V60: audit_entity_config — per-entity toggle for the data-change audit AOP
-- aspect (spec 07-audit.md §Decisiones 1-2, 6). `entity_key` must match the
-- `entity` attribute of @Auditable on the corresponding *Service method.
--
-- Fail-safe by construction: DataChangeAuditAspect treats a MISSING row the
-- same as `enabled=false` (skip + warn, never block the business operation) —
-- see spec §Decisiones 6. This seed does not need to be exhaustive on day one.
--
-- `subsidy` is intentionally included even though `subsidy_audit_log` already
-- exists as a hand-rolled business bitácora: `data_change_audit_log` also
-- captures DELETE with full before_json, which subsidy_audit_log does not
-- guarantee — both logs coexist intentionally (spec 07-audit.md, note below
-- the V60 DDL).
-- ────────────────────────────────────────────────────────────────────────────

CREATE TABLE audit_entity_config
(
    audit_entity_config_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                    UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    entity_key              VARCHAR(80)  NOT NULL UNIQUE,
    display_name            VARCHAR(120) NOT NULL,
    table_name              VARCHAR(120),
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_create            BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_update            BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_delete            BOOLEAN      NOT NULL DEFAULT TRUE,
    audit_report            BOOLEAN      NOT NULL DEFAULT TRUE,
    capture_before_after    BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                   TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID
);

CREATE INDEX idx_audit_entity_config_key ON audit_entity_config (entity_key);

CREATE TRIGGER trg_audit_entity_config_updated_at
    BEFORE UPDATE ON audit_entity_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed: core business entities with a write path today. Not exhaustive —
-- new entities are onboarded by inserting a row here (no redeploy needed,
-- see spec §Endpoints admin: PATCH /v1/admin/audit/config/{entityKey}).
INSERT INTO audit_entity_config (entity_key, display_name, table_name) VALUES
    ('ally',                  'Aliados',                          'allies'),
    ('ally_agreement',        'Acuerdos de aliado',                'ally_agreements'),
    ('ally_service',          'Servicios de aliado',               'ally_services'),
    ('ally_user',             'Usuarios de aliado',                'ally_users'),
    ('benefit_usage',         'Uso de beneficios',                  'benefit_usages'),
    ('corporate_contract',    'Contratos corporativos',            'corporate_contracts'),
    ('member',                'Afiliados',                         'members'),
    ('beneficiary',           'Beneficiarios',                      'beneficiaries'),
    ('medical_record',        'Historial médico',                  'medical_records'),
    ('member_document',       'Documentos de afiliado',            'member_documents'),
    ('member_promoter',       'Asignación afiliado-promotor',       'member_promoter_assignments'),
    ('membership',            'Membresías',                        'memberships'),
    ('plan',                  'Planes',                            'plans'),
    ('payment',               'Pagos',                             'payments'),
    ('person',                'Personas',                           'persons'),
    ('promoter',              'Promotores',                        'promoters'),
    ('bonus_award',           'Bonos otorgados',                    'bonus_awards'),
    ('bonus_rule',            'Reglas de bono',                     'bonus_rules'),
    ('commission',            'Comisiones',                         'commissions'),
    ('commission_tier',       'Escalas de comisión',                'commission_tiers'),
    ('referral',              'Referidos',                          'referrals'),
    ('referral_code',         'Códigos de referido',                'referral_codes'),
    ('subsidy',                'Subsidios',                          'subsidies'),
    ('role',                  'Roles',                              'roles'),
    ('user',                  'Usuarios',                           'users'),
    ('scheduled_job',         'Trabajos programados',               'scheduled_jobs'),
    ('ally_type',              'Tipos de aliado (catálogo)',         'ally_types');
