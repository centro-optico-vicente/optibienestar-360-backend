package com.fenixcore.optibienestar360.modules.birthday.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read-only lookups behind {@code GET /v1/me/birthdays}. Native SQL so the
 * advisor's portfolio (holders + active beneficiaries) is filtered by birthday
 * month-day in a single round-trip, without loading entities.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BirthdayQueries {

    private final NamedParameterJdbcTemplate jdbc;

    /** A person of an advisor's portfolio whose birthday falls on one of the requested month-days. */
    public record PortfolioPerson(
            UUID memberUuid,
            boolean beneficiary,
            String fullName,
            String holderName,
            String relationship,
            String phone,
            LocalDate birthDate
    ) {}

    /**
     * Active holders attributed to the promoter and their active beneficiaries whose
     * birth date matches one of {@code monthDays} ({@code MM-dd}).
     */
    public List<PortfolioPerson> findPortfolioBirthdays(Long promoterId, Collection<String> monthDays) {
        if (monthDays.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("promoterId", promoterId)
                .addValue("monthDays", monthDays);
        return jdbc.query("""
                SELECT m.uuid AS member_uuid, false AS is_beneficiary, p.full_name,
                       CAST(NULL AS varchar) AS holder_name, CAST(NULL AS varchar) AS relationship,
                       p.phone, p.birth_date
                FROM members m
                JOIN persons p ON p.persons_id = m.person_id
                WHERE m.is_active AND m.promoter_id = :promoterId
                  AND p.birth_date IS NOT NULL
                  AND to_char(p.birth_date, 'MM-DD') IN (:monthDays)
                UNION ALL
                SELECT m.uuid, true, p.full_name, hp.full_name, b.relationship, p.phone, p.birth_date
                FROM beneficiaries b
                JOIN members m ON m.members_id = b.member_id
                JOIN persons p ON p.persons_id = b.person_id
                JOIN persons hp ON hp.persons_id = m.person_id
                WHERE b.is_active AND m.is_active AND m.promoter_id = :promoterId
                  AND p.birth_date IS NOT NULL
                  AND to_char(p.birth_date, 'MM-DD') IN (:monthDays)
                """, params, (rs, i) -> new PortfolioPerson(
                rs.getObject("member_uuid", UUID.class),
                rs.getBoolean("is_beneficiary"),
                rs.getString("full_name"),
                rs.getString("holder_name"),
                rs.getString("relationship"),
                rs.getString("phone"),
                rs.getObject("birth_date", LocalDate.class)));
    }
}
