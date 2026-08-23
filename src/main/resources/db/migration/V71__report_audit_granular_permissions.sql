SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V72: per-domain permission to view the *report generation* audit trail
-- (report_audit_log — who generated which report, when), mirroring V66's
-- <DOMAIN>_AUDIT_VIEW (data-change history) but for GET /v1/admin/audit/reports
-- scoped by entityKey. REPORT_AUDIT_VIEW_ALL (V70) remains the cross-entity
-- override.
--
-- Named <DOMAIN>_REPORT_AUDIT_VIEW (not <DOMAIN>_AUDIT_VIEW, already taken by
-- V66 for the data-change trail) and distinct from <DOMAIN>_REPORT_GENERATE
-- (V66, permission to *create* a report) — a role may generate promoter
-- reports without being able to see who else generated/downloaded them, and
-- vice versa.
--
-- Not applied to REPORTS: V66 already covers that domain's own audit trail
-- via REPORT_AUDIT_VIEW (reports-panel config history), a different concern.
--
-- SYSTEM receives every new permission automatically via the V30 trigger.
-- ADMINISTRADOR gets explicit grants for the same reason as V66/V70.
-- ────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (name, domain_id, description)
SELECT v.name, pd.permission_domains_id, v.description
FROM (VALUES
	('USER_REPORT_AUDIT_VIEW',        'USERS',       'Ver el historial de reportes generados de usuarios'),
	('MEMBER_REPORT_AUDIT_VIEW',      'MEMBERS',     'Ver el historial de reportes generados de afiliados'),
	('ALLY_REPORT_AUDIT_VIEW',        'ALLIES',      'Ver el historial de reportes generados de aliados'),
	('PLAN_REPORT_AUDIT_VIEW',        'PLANS',       'Ver el historial de reportes generados de planes'),
	('MEMBERSHIP_REPORT_AUDIT_VIEW',  'MEMBERSHIPS', 'Ver el historial de reportes generados de membresías'),
	('PAYMENT_REPORT_AUDIT_VIEW',     'PAYMENTS',    'Ver el historial de reportes generados de pagos'),
	('PROMOTER_REPORT_AUDIT_VIEW',    'PROMOTERS',   'Ver el historial de reportes generados de promotores'),
	('COMMISSION_REPORT_AUDIT_VIEW',  'COMMISSIONS', 'Ver el historial de reportes generados de comisiones'),
	('REFERRAL_REPORT_AUDIT_VIEW',    'REFERRALS',   'Ver el historial de reportes generados de referidos')
) AS v(name, domain_code, description)
JOIN permission_domains pd ON pd.code = v.domain_code;


-- ADMINISTRADOR: full access to all new report-audit permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.roles_id, p.permissions_id
FROM roles r
		CROSS JOIN permissions p
WHERE r.name = 'ADMINISTRADOR'
AND p.name IN (
	'USER_REPORT_AUDIT_VIEW',
	'MEMBER_REPORT_AUDIT_VIEW',
	'ALLY_REPORT_AUDIT_VIEW',
	'PLAN_REPORT_AUDIT_VIEW',
	'MEMBERSHIP_REPORT_AUDIT_VIEW',
	'PAYMENT_REPORT_AUDIT_VIEW',
	'PROMOTER_REPORT_AUDIT_VIEW',
	'COMMISSION_REPORT_AUDIT_VIEW',
	'REFERRAL_REPORT_AUDIT_VIEW'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
	new_permission TEXT;
BEGIN
	FOREACH new_permission IN ARRAY ARRAY[
		'USER_REPORT_AUDIT_VIEW',
		'MEMBER_REPORT_AUDIT_VIEW',
		'ALLY_REPORT_AUDIT_VIEW',
		'PLAN_REPORT_AUDIT_VIEW',
		'MEMBERSHIP_REPORT_AUDIT_VIEW',
		'PAYMENT_REPORT_AUDIT_VIEW',
		'PROMOTER_REPORT_AUDIT_VIEW',
		'COMMISSION_REPORT_AUDIT_VIEW',
		'REFERRAL_REPORT_AUDIT_VIEW'
	]
	LOOP
		IF NOT EXISTS (SELECT 1 FROM permissions WHERE name = new_permission) THEN
			RAISE EXCEPTION 'V72: % was not created', new_permission;
		END IF;

		IF NOT EXISTS (
			SELECT 1 FROM role_permissions rp
					JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'SYSTEM'
					JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
		) THEN
			RAISE EXCEPTION 'V72: SYSTEM did not receive % (V30 trigger?)', new_permission;
		END IF;

		IF NOT EXISTS (
			SELECT 1 FROM role_permissions rp
					JOIN roles r       ON r.roles_id = rp.role_id AND r.name = 'ADMINISTRADOR'
					JOIN permissions p ON p.permissions_id = rp.permission_id AND p.name = new_permission
		) THEN
			RAISE EXCEPTION 'V72: ADMINISTRADOR did not receive %', new_permission;
		END IF;
	END LOOP;
END $$;
