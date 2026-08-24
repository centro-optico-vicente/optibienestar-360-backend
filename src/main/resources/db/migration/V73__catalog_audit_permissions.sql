SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V73: audit permissions for the 10 catalog/master-data entities (country,
-- state, city, gender, document_type, marital_status, occupation,
-- medical_specialty, service_category, promoter_type) — the last group of
-- entities left unwired for audit after V66/V71.
--
-- Same shape as V66/V71: one <ENTITY>_AUDIT_VIEW/_REPORT_AUDIT_VIEW pair PER
-- CATALOG, not a shared pair — even though these are low-churn lookup tables
-- governed by a shared read permission (CATALOG_VIEW_ALL, V34), the audit
-- trail must stay delegable per catalog (e.g. a role that audits countries
-- without also auditing genders), matching the granular pattern already used
-- for every other domain. All 10 pairs live in the CATALOGS domain (V32).
--
-- SYSTEM receives every new permission automatically via the V30 trigger.
-- ADMINISTRADOR gets explicit grants for the same reason as V64/V66/V71.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
	('COUNTRY_AUDIT_VIEW',            'Ver el historial de cambios de un país'),
	('COUNTRY_REPORT_AUDIT_VIEW',     'Ver el historial de reportes generados de países'),

	('STATE_AUDIT_VIEW',              'Ver el historial de cambios de un estado'),
	('STATE_REPORT_AUDIT_VIEW',       'Ver el historial de reportes generados de estados'),

	('CITY_AUDIT_VIEW',               'Ver el historial de cambios de una ciudad'),
	('CITY_REPORT_AUDIT_VIEW',        'Ver el historial de reportes generados de ciudades'),

	('GENDER_AUDIT_VIEW',             'Ver el historial de cambios de un género'),
	('GENDER_REPORT_AUDIT_VIEW',      'Ver el historial de reportes generados de géneros'),

	('DOCUMENT_TYPE_AUDIT_VIEW',        'Ver el historial de cambios de un tipo de documento'),
	('DOCUMENT_TYPE_REPORT_AUDIT_VIEW', 'Ver el historial de reportes generados de tipos de documento'),

	('MARITAL_STATUS_AUDIT_VIEW',        'Ver el historial de cambios de un estado civil'),
	('MARITAL_STATUS_REPORT_AUDIT_VIEW', 'Ver el historial de reportes generados de estados civiles'),

	('OCCUPATION_AUDIT_VIEW',         'Ver el historial de cambios de una ocupación'),
	('OCCUPATION_REPORT_AUDIT_VIEW',  'Ver el historial de reportes generados de ocupaciones'),

	('MEDICAL_SPECIALTY_AUDIT_VIEW',        'Ver el historial de cambios de una especialidad médica'),
	('MEDICAL_SPECIALTY_REPORT_AUDIT_VIEW', 'Ver el historial de reportes generados de especialidades médicas'),

	('SERVICE_CATEGORY_AUDIT_VIEW',        'Ver el historial de cambios de una categoría de servicio'),
	('SERVICE_CATEGORY_REPORT_AUDIT_VIEW', 'Ver el historial de reportes generados de categorías de servicio'),

	('PROMOTER_TYPE_AUDIT_VIEW',        'Ver el historial de cambios de un tipo de promotor'),
	('PROMOTER_TYPE_REPORT_AUDIT_VIEW', 'Ver el historial de reportes generados de tipos de promotor')
) AS v(name, description)
CROSS JOIN permission_domains pd
WHERE pd.code = 'CATALOGS';


-- ADMINISTRADOR: full access to all new catalog audit permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
		CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
AND p.name IN (
	'COUNTRY_AUDIT_VIEW', 'COUNTRY_REPORT_AUDIT_VIEW',
	'STATE_AUDIT_VIEW', 'STATE_REPORT_AUDIT_VIEW',
	'CITY_AUDIT_VIEW', 'CITY_REPORT_AUDIT_VIEW',
	'GENDER_AUDIT_VIEW', 'GENDER_REPORT_AUDIT_VIEW',
	'DOCUMENT_TYPE_AUDIT_VIEW', 'DOCUMENT_TYPE_REPORT_AUDIT_VIEW',
	'MARITAL_STATUS_AUDIT_VIEW', 'MARITAL_STATUS_REPORT_AUDIT_VIEW',
	'OCCUPATION_AUDIT_VIEW', 'OCCUPATION_REPORT_AUDIT_VIEW',
	'MEDICAL_SPECIALTY_AUDIT_VIEW', 'MEDICAL_SPECIALTY_REPORT_AUDIT_VIEW',
	'SERVICE_CATEGORY_AUDIT_VIEW', 'SERVICE_CATEGORY_REPORT_AUDIT_VIEW',
	'PROMOTER_TYPE_AUDIT_VIEW', 'PROMOTER_TYPE_REPORT_AUDIT_VIEW'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
	new_permission TEXT;
BEGIN
	FOREACH new_permission IN ARRAY ARRAY[
		'COUNTRY_AUDIT_VIEW', 'COUNTRY_REPORT_AUDIT_VIEW',
		'STATE_AUDIT_VIEW', 'STATE_REPORT_AUDIT_VIEW',
		'CITY_AUDIT_VIEW', 'CITY_REPORT_AUDIT_VIEW',
		'GENDER_AUDIT_VIEW', 'GENDER_REPORT_AUDIT_VIEW',
		'DOCUMENT_TYPE_AUDIT_VIEW', 'DOCUMENT_TYPE_REPORT_AUDIT_VIEW',
		'MARITAL_STATUS_AUDIT_VIEW', 'MARITAL_STATUS_REPORT_AUDIT_VIEW',
		'OCCUPATION_AUDIT_VIEW', 'OCCUPATION_REPORT_AUDIT_VIEW',
		'MEDICAL_SPECIALTY_AUDIT_VIEW', 'MEDICAL_SPECIALTY_REPORT_AUDIT_VIEW',
		'SERVICE_CATEGORY_AUDIT_VIEW', 'SERVICE_CATEGORY_REPORT_AUDIT_VIEW',
		'PROMOTER_TYPE_AUDIT_VIEW', 'PROMOTER_TYPE_REPORT_AUDIT_VIEW'
	]
	LOOP
		IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
			RAISE EXCEPTION 'V73: % was not created', new_permission;
		END IF;

		IF NOT EXISTS (
			SELECT 1 FROM role_permissions rp
					JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
					JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
		) THEN
			RAISE EXCEPTION 'V73: SYSTEM did not receive % (V30 trigger?)', new_permission;
		END IF;

		IF NOT EXISTS (
			SELECT 1 FROM role_permissions rp
					JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
					JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
		) THEN
			RAISE EXCEPTION 'V73: ADMINISTRADOR did not receive %', new_permission;
		END IF;
	END LOOP;
END $$;
