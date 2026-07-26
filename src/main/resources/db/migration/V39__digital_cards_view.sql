SET search_path TO app, public;

-- V39: digital_cards_view — denormalized read model for the affiliate digital
-- card (vertical-9, GET /v1/me/digital-card). One row per active member,
-- joining the identity hub (persons), the currently-active membership (V21
-- partial UNIQUE guarantees at most one) and its plan, plus the member's user
-- account (1:1 with persons) so the card can be fetched by the authenticated
-- caller in a single lookup.
--
-- Read-only: mapped by an @Immutable JPA entity; the QR image is generated
-- application-side (ZXing) and is not part of the view.

CREATE VIEW digital_cards_view AS
SELECT
    m.uuid              AS member_uuid,
    u.uuid              AS user_uuid,
    p.full_name         AS full_name,
    p.document_type     AS document_type,
    p.document_number   AS document_number,
    pl.name             AS plan_name,
    ms.status           AS membership_status,
    ms.next_due_date    AS next_due_date,
    m.enrolled_at       AS member_since
FROM members m
    JOIN persons p            ON p.persons_id = m.person_id
    LEFT JOIN users u         ON u.person_id = p.persons_id
    LEFT JOIN memberships ms  ON ms.member_id = m.members_id AND ms.is_active = TRUE
    LEFT JOIN plans pl        ON pl.plans_id = ms.plan_id
WHERE m.is_active = TRUE;

COMMENT ON VIEW digital_cards_view IS
    'Vertical-9 read model for the affiliate digital card: one row per active member with person, active membership + plan, and the linked user account.';
