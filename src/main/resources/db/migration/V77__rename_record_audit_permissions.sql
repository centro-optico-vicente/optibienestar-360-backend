SET search_path TO app, public;

-- ────────────────────────────────────────────────────────────────────────────
-- V77: rename every <DOMAIN>_AUDIT_VIEW → <DOMAIN>_RECORD_AUDIT_VIEW and every
-- <DOMAIN>_AUDIT_RESTORE → <DOMAIN>_RECORD_AUDIT_RESTORE (V66/V73). Both names
-- used to end in "_AUDIT_VIEW", the same suffix as the unrelated
-- <DOMAIN>_REPORT_AUDIT_VIEW permission (V71/V73 — "who generated reports"),
-- which forced consumers doing suffix matching (the admin roles UI's bulk
-- "mark by action" toggles) to special-case the collision. Renaming removes
-- it at the source: no permission name is ever a suffix of another anymore.
--
-- ROLE_RECORD_AUDIT_VIEW/ROLE_RECORD_AUDIT_RESTORE (V75) aren't listed below —
-- V75 minted them with the final RECORD name directly since it's a brand new
-- permission with nothing to rename.
--
-- `role_permissions` references permissions by surrogate id, never by name,
-- so this UPDATE preserves every role's existing grants untouched — no
-- @PreAuthorize annotation hardcodes these strings either (they're all read
-- dynamically through AuditEntityAccess's maps, updated alongside this
-- migration). Explicit name-to-name pairs, not a pattern/LIKE rename, so
-- REPORT_AUDIT_VIEW (itself a valid <DOMAIN>_AUDIT_VIEW for the REPORTS
-- domain) can't be confused with the *_REPORT_AUDIT_VIEW family.
-- ────────────────────────────────────────────────────────────────────────────

UPDATE permissions p
SET name = v.new_name
FROM (VALUES
    ('ALLY_AUDIT_VIEW',             'ALLY_RECORD_AUDIT_VIEW'),
    ('CITY_AUDIT_VIEW',             'CITY_RECORD_AUDIT_VIEW'),
    ('COMMISSION_AUDIT_VIEW',       'COMMISSION_RECORD_AUDIT_VIEW'),
    ('COUNTRY_AUDIT_VIEW',          'COUNTRY_RECORD_AUDIT_VIEW'),
    ('DOCUMENT_TYPE_AUDIT_VIEW',    'DOCUMENT_TYPE_RECORD_AUDIT_VIEW'),
    ('GENDER_AUDIT_VIEW',           'GENDER_RECORD_AUDIT_VIEW'),
    ('MARITAL_STATUS_AUDIT_VIEW',   'MARITAL_STATUS_RECORD_AUDIT_VIEW'),
    ('MEDICAL_SPECIALTY_AUDIT_VIEW', 'MEDICAL_SPECIALTY_RECORD_AUDIT_VIEW'),
    ('MEMBER_AUDIT_VIEW',           'MEMBER_RECORD_AUDIT_VIEW'),
    ('MEMBERSHIP_AUDIT_VIEW',       'MEMBERSHIP_RECORD_AUDIT_VIEW'),
    ('OCCUPATION_AUDIT_VIEW',       'OCCUPATION_RECORD_AUDIT_VIEW'),
    ('PAYMENT_AUDIT_VIEW',          'PAYMENT_RECORD_AUDIT_VIEW'),
    ('PLAN_AUDIT_VIEW',             'PLAN_RECORD_AUDIT_VIEW'),
    ('PROMOTER_AUDIT_VIEW',         'PROMOTER_RECORD_AUDIT_VIEW'),
    ('PROMOTER_TYPE_AUDIT_VIEW',    'PROMOTER_TYPE_RECORD_AUDIT_VIEW'),
    ('REFERRAL_AUDIT_VIEW',         'REFERRAL_RECORD_AUDIT_VIEW'),
    ('REPORT_AUDIT_VIEW',           'REPORT_RECORD_AUDIT_VIEW'),
    ('SERVICE_CATEGORY_AUDIT_VIEW', 'SERVICE_CATEGORY_RECORD_AUDIT_VIEW'),
    ('STATE_AUDIT_VIEW',            'STATE_RECORD_AUDIT_VIEW'),
    ('USER_AUDIT_VIEW',             'USER_RECORD_AUDIT_VIEW'),

    ('ALLY_AUDIT_RESTORE',          'ALLY_RECORD_AUDIT_RESTORE'),
    ('COMMISSION_AUDIT_RESTORE',    'COMMISSION_RECORD_AUDIT_RESTORE'),
    ('MEMBER_AUDIT_RESTORE',        'MEMBER_RECORD_AUDIT_RESTORE'),
    ('MEMBERSHIP_AUDIT_RESTORE',    'MEMBERSHIP_RECORD_AUDIT_RESTORE'),
    ('PAYMENT_AUDIT_RESTORE',       'PAYMENT_RECORD_AUDIT_RESTORE'),
    ('PLAN_AUDIT_RESTORE',          'PLAN_RECORD_AUDIT_RESTORE'),
    ('PROMOTER_AUDIT_RESTORE',      'PROMOTER_RECORD_AUDIT_RESTORE'),
    ('REFERRAL_AUDIT_RESTORE',      'REFERRAL_RECORD_AUDIT_RESTORE'),
    ('REPORT_AUDIT_RESTORE',        'REPORT_RECORD_AUDIT_RESTORE'),
    ('USER_AUDIT_RESTORE',          'USER_RECORD_AUDIT_RESTORE')
) AS v(old_name, new_name)
WHERE p.name = v.old_name;


-- ─── Fail loudly rather than migrate into a half-applied state ──────────────
DO $$
DECLARE
    renamed_count INT;
    old_name TEXT;
    new_name TEXT;
BEGIN
    SELECT COUNT(*) INTO renamed_count
    FROM permissions
    WHERE name LIKE '%\_RECORD\_AUDIT\_VIEW' OR name LIKE '%\_RECORD\_AUDIT\_RESTORE';
    IF renamed_count <> 32 THEN
        RAISE EXCEPTION 'V77: expected 32 renamed permissions, found %', renamed_count;
    END IF;

    FOREACH old_name IN ARRAY ARRAY[
        'ALLY_AUDIT_VIEW', 'CITY_AUDIT_VIEW', 'COMMISSION_AUDIT_VIEW', 'COUNTRY_AUDIT_VIEW',
        'DOCUMENT_TYPE_AUDIT_VIEW', 'GENDER_AUDIT_VIEW', 'MARITAL_STATUS_AUDIT_VIEW',
        'MEDICAL_SPECIALTY_AUDIT_VIEW', 'MEMBER_AUDIT_VIEW', 'MEMBERSHIP_AUDIT_VIEW',
        'OCCUPATION_AUDIT_VIEW', 'PAYMENT_AUDIT_VIEW', 'PLAN_AUDIT_VIEW', 'PROMOTER_AUDIT_VIEW',
        'PROMOTER_TYPE_AUDIT_VIEW', 'REFERRAL_AUDIT_VIEW', 'REPORT_AUDIT_VIEW',
        'SERVICE_CATEGORY_AUDIT_VIEW', 'STATE_AUDIT_VIEW', 'USER_AUDIT_VIEW',
        'ALLY_AUDIT_RESTORE', 'COMMISSION_AUDIT_RESTORE', 'MEMBER_AUDIT_RESTORE',
        'MEMBERSHIP_AUDIT_RESTORE', 'PAYMENT_AUDIT_RESTORE', 'PLAN_AUDIT_RESTORE',
        'PROMOTER_AUDIT_RESTORE', 'REFERRAL_AUDIT_RESTORE', 'REPORT_AUDIT_RESTORE',
        'USER_AUDIT_RESTORE'
    ]
    LOOP
        IF EXISTS (SELECT 1 FROM permissions WHERE name = old_name) THEN
            RAISE EXCEPTION 'V77: old name % still present after rename', old_name;
        END IF;
    END LOOP;
END $$;
