SET search_path TO app, public;

-- ============================================================================
-- V119: closes two gaps left open by the payments/payouts unification
-- (V115-V118, hub plan ".ai/plans/2026-09-17-payments-unification-plan.md")
-- that only touched raw DDL:
--
--   1. entity_config (V60/V80) — DataChangeAuditAspect treats a MISSING row
--      the same as enabled=false (fail-safe by design, spec 07-audit.md
--      §Decisión 6). `payment` was already registered (V60); the 4 new
--      tables from V115-V117 were not, so any future @Auditable on their
--      services would silently no-op until this row exists — same gap V100
--      closed for currency/exchange_rate/organization.
--   2. permissions — the 3 new admin catalogs (banks, payment_categories,
--      payment_methods) had no permissions at all. New domain
--      PAYMENT_CATALOG groups them, distinct from PAYMENTS (which is about
--      the payment transactions themselves, not their supporting catalogs).
--
-- Each catalog gets the FULL standard master-screen set (6 permissions, not
-- just CRUD) — same shape V73 established for the 10 catalog/master-data
-- entities (country, gender, document_type, etc.) and V66/V71 for bigger
-- entities:
--   <ENTITY>_VIEW_ALL / _CREATE / _UPDATE / _DELETE   — the catalog itself
--   <ENTITY>_RECORD_AUDIT_VIEW                         — change history of a row
--   <ENTITY>_REPORT_AUDIT_VIEW                         — history of reports generated about the catalog
-- No <ENTITY>_REPORT_GENERATE: that verb is reserved for entities with an
-- actual report-generation feature (USER, MEMBER, ALLY, PLAN, MEMBERSHIP,
-- PAYMENT, PROMOTER, COMMISSION, REFERRAL — V66) — a bank/method/category
-- catalog has no report of its own to generate, only a change-audit trail
-- and (if ever reported on elsewhere) a record of when that happened.
--
-- (CURRENCY_* from V96 only got the 4 CRUD verbs, no audit pair — an
-- oversight relative to this standard, not something to replicate here.)
--
-- payment_lines is registered in entity_config but gets NO permission set of
-- its own — it has no standalone admin screen (plan §"Pantallas requeridas":
-- lines are edited as part of a payment, never listed on their own), so its
-- audit config exists for traceability but its write permissions ride on
-- PAYMENT_CREATE/PAYMENT_APPROVE/PAYMENT_REJECT (V6/V79), same as payments
-- today.
-- ============================================================================

-- ─── 1. entity_config ────────────────────────────────────────────────────────

INSERT INTO entity_config (entity_key, display_name, table_name) VALUES
    ('bank',              'Bancos',              'banks'),
    ('payment_category',  'Categorías de pago',  'payment_categories'),
    ('payment_method',    'Métodos de pago',     'payment_methods'),
    ('payment_line',      'Líneas de pago',      'payment_lines')
ON CONFLICT (entity_key) DO NOTHING;

-- ─── 2. permissions ──────────────────────────────────────────────────────────

INSERT INTO permission_domains (code, name, icon, description, display_order)
VALUES ('PAYMENT_CATALOG', 'Catálogos de pago', 'i-lucide-landmark',
        'Bancos, categorías y métodos de pago (soporte de payments/payment_lines)', 122)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
    ('BANK_VIEW_ALL',                  'PAYMENT_CATALOG', 'Ver el catálogo de bancos'),
    ('BANK_CREATE',                    'PAYMENT_CATALOG', 'Crear un banco'),
    ('BANK_UPDATE',                    'PAYMENT_CATALOG', 'Actualizar un banco'),
    ('BANK_DELETE',                    'PAYMENT_CATALOG', 'Desactivar un banco'),
    ('BANK_RECORD_AUDIT_VIEW',         'PAYMENT_CATALOG', 'Ver el historial de cambios de un banco'),
    ('BANK_REPORT_AUDIT_VIEW',         'PAYMENT_CATALOG', 'Ver el historial de reportes generados de bancos'),

    ('PAYMENT_CATEGORY_VIEW_ALL',          'PAYMENT_CATALOG', 'Ver el catálogo de categorías de pago'),
    ('PAYMENT_CATEGORY_CREATE',            'PAYMENT_CATALOG', 'Crear una categoría de pago'),
    ('PAYMENT_CATEGORY_UPDATE',            'PAYMENT_CATALOG', 'Actualizar una categoría de pago'),
    ('PAYMENT_CATEGORY_DELETE',            'PAYMENT_CATALOG', 'Desactivar una categoría de pago'),
    ('PAYMENT_CATEGORY_RECORD_AUDIT_VIEW', 'PAYMENT_CATALOG', 'Ver el historial de cambios de una categoría de pago'),
    ('PAYMENT_CATEGORY_REPORT_AUDIT_VIEW', 'PAYMENT_CATALOG', 'Ver el historial de reportes generados de categorías de pago'),

    ('PAYMENT_METHOD_VIEW_ALL',          'PAYMENT_CATALOG', 'Ver el catálogo de métodos de pago'),
    ('PAYMENT_METHOD_CREATE',            'PAYMENT_CATALOG', 'Crear un método de pago'),
    ('PAYMENT_METHOD_UPDATE',            'PAYMENT_CATALOG', 'Actualizar un método de pago'),
    ('PAYMENT_METHOD_DELETE',            'PAYMENT_CATALOG', 'Desactivar un método de pago'),
    ('PAYMENT_METHOD_RECORD_AUDIT_VIEW', 'PAYMENT_CATALOG', 'Ver el historial de cambios de un método de pago'),
    ('PAYMENT_METHOD_REPORT_AUDIT_VIEW', 'PAYMENT_CATALOG', 'Ver el historial de reportes generados de métodos de pago')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;

-- SYSTEM / ADMINISTRADOR: all 18 new permissions — same trust level as
-- CURRENCY_* (V96), these catalogs are just as foundational to payments.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name IN ('SYSTEM', 'ADMINISTRADOR')
  AND p.name IN ('BANK_VIEW_ALL', 'BANK_CREATE', 'BANK_UPDATE', 'BANK_DELETE',
                 'BANK_RECORD_AUDIT_VIEW', 'BANK_REPORT_AUDIT_VIEW',
                 'PAYMENT_CATEGORY_VIEW_ALL', 'PAYMENT_CATEGORY_CREATE', 'PAYMENT_CATEGORY_UPDATE', 'PAYMENT_CATEGORY_DELETE',
                 'PAYMENT_CATEGORY_RECORD_AUDIT_VIEW', 'PAYMENT_CATEGORY_REPORT_AUDIT_VIEW',
                 'PAYMENT_METHOD_VIEW_ALL', 'PAYMENT_METHOD_CREATE', 'PAYMENT_METHOD_UPDATE', 'PAYMENT_METHOD_DELETE',
                 'PAYMENT_METHOD_RECORD_AUDIT_VIEW', 'PAYMENT_METHOD_REPORT_AUDIT_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM (VALUES ('bank'), ('payment_category'), ('payment_method'), ('payment_line')) AS want(entity_key)
        WHERE NOT EXISTS (SELECT 1 FROM entity_config c WHERE c.entity_key = want.entity_key)
    ) THEN
        RAISE EXCEPTION 'V119: one or more entity_config rows were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM (VALUES
            ('BANK_VIEW_ALL'), ('BANK_CREATE'), ('BANK_UPDATE'), ('BANK_DELETE'),
            ('BANK_RECORD_AUDIT_VIEW'), ('BANK_REPORT_AUDIT_VIEW'),
            ('PAYMENT_CATEGORY_VIEW_ALL'), ('PAYMENT_CATEGORY_CREATE'), ('PAYMENT_CATEGORY_UPDATE'), ('PAYMENT_CATEGORY_DELETE'),
            ('PAYMENT_CATEGORY_RECORD_AUDIT_VIEW'), ('PAYMENT_CATEGORY_REPORT_AUDIT_VIEW'),
            ('PAYMENT_METHOD_VIEW_ALL'), ('PAYMENT_METHOD_CREATE'), ('PAYMENT_METHOD_UPDATE'), ('PAYMENT_METHOD_DELETE'),
            ('PAYMENT_METHOD_RECORD_AUDIT_VIEW'), ('PAYMENT_METHOD_REPORT_AUDIT_VIEW')
        ) AS want(name)
        WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.name = want.name)
    ) THEN
        RAISE EXCEPTION 'V119: one or more new permissions were not created';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('BANK_VIEW_ALL', 'BANK_CREATE', 'BANK_UPDATE', 'BANK_DELETE',
                          'BANK_RECORD_AUDIT_VIEW', 'BANK_REPORT_AUDIT_VIEW',
                          'PAYMENT_CATEGORY_VIEW_ALL', 'PAYMENT_CATEGORY_CREATE', 'PAYMENT_CATEGORY_UPDATE', 'PAYMENT_CATEGORY_DELETE',
                          'PAYMENT_CATEGORY_RECORD_AUDIT_VIEW', 'PAYMENT_CATEGORY_REPORT_AUDIT_VIEW',
                          'PAYMENT_METHOD_VIEW_ALL', 'PAYMENT_METHOD_CREATE', 'PAYMENT_METHOD_UPDATE', 'PAYMENT_METHOD_DELETE',
                          'PAYMENT_METHOD_RECORD_AUDIT_VIEW', 'PAYMENT_METHOD_REPORT_AUDIT_VIEW')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V119: SYSTEM did not receive the new permissions (V30 trigger?)';
    END IF;

    IF EXISTS (
        SELECT 1 FROM permissions p
        WHERE p.name IN ('BANK_VIEW_ALL', 'BANK_CREATE', 'BANK_UPDATE', 'BANK_DELETE',
                          'BANK_RECORD_AUDIT_VIEW', 'BANK_REPORT_AUDIT_VIEW',
                          'PAYMENT_CATEGORY_VIEW_ALL', 'PAYMENT_CATEGORY_CREATE', 'PAYMENT_CATEGORY_UPDATE', 'PAYMENT_CATEGORY_DELETE',
                          'PAYMENT_CATEGORY_RECORD_AUDIT_VIEW', 'PAYMENT_CATEGORY_REPORT_AUDIT_VIEW',
                          'PAYMENT_METHOD_VIEW_ALL', 'PAYMENT_METHOD_CREATE', 'PAYMENT_METHOD_UPDATE', 'PAYMENT_METHOD_DELETE',
                          'PAYMENT_METHOD_RECORD_AUDIT_VIEW', 'PAYMENT_METHOD_REPORT_AUDIT_VIEW')
          AND NOT EXISTS (
            SELECT 1 FROM role_permissions rp
                     JOIN roles r ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
            WHERE rp.permission_id = p.permissions_id)
    ) THEN
        RAISE EXCEPTION 'V119: ADMINISTRADOR did not receive the new permissions';
    END IF;
END $$;
