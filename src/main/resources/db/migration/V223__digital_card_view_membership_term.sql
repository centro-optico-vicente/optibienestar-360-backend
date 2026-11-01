SET search_path TO app, public;

-- ============================================================================
-- V223: expose the membership term on the digital card view (hub ADR 0034).
--
-- Appends expires_at and inscription_due_since at the END of digital_cards_view
-- (CREATE OR REPLACE VIEW only allows new columns after the existing ones), so
-- the affiliate portal can show when the coverage ends and whether a
-- re-inscription is pending.
-- ============================================================================
CREATE OR REPLACE VIEW digital_cards_view AS
SELECT
    m.uuid                  AS member_uuid,
    u.uuid                  AS user_uuid,
    p.full_name             AS full_name,
    p.document_type         AS document_type,
    p.document_number       AS document_number,
    pl.name                 AS plan_name,
    ms.status               AS membership_status,
    ms.next_due_date        AS next_due_date,
    m.enrolled_at           AS member_since,
    ms.expires_at           AS expires_at,
    ms.inscription_due_since AS inscription_due_since
FROM members m
    JOIN persons p            ON p.persons_id = m.person_id
    LEFT JOIN users u         ON u.person_id = p.persons_id
    LEFT JOIN memberships ms  ON ms.member_id = m.members_id AND ms.is_active = TRUE
    LEFT JOIN plans pl        ON pl.plans_id = ms.plan_id
WHERE m.is_active = TRUE;
