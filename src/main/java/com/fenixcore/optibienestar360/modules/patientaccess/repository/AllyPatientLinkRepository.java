package com.fenixcore.optibienestar360.modules.patientaccess.repository;

import com.fenixcore.optibienestar360.modules.patientaccess.entity.AllyPatientLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllyPatientLinkRepository extends JpaRepository<AllyPatientLink, Long> {

    Optional<AllyPatientLink> findByUuid(UUID uuid);

    @Query("SELECT l FROM AllyPatientLink l " +
           "WHERE l.doctorPerson.id = :doctorPersonId AND l.member.id = :memberId AND l.status = 'ACTIVE'")
    Optional<AllyPatientLink> findActive(@Param("doctorPersonId") Long doctorPersonId,
                                         @Param("memberId") Long memberId);

    /**
     * The doctor's patients ("Mis pacientes"), newest link first. {@code q} (blank = no filter) matches the
     * patient's name or document, ignoring case and accents.
     */
    @Query(value = "SELECT l FROM AllyPatientLink l JOIN FETCH l.member m JOIN FETCH m.person p " +
                   "WHERE l.doctorPerson.id = :doctorPersonId AND l.status = 'ACTIVE' " +
                   "AND (:q IS NULL OR :q = '' OR " +
                   "cast(function('immutable_unaccent', lower(p.fullName)) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) OR " +
                   "cast(function('immutable_unaccent', lower(p.documentNumber)) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string)) " +
                   "AND (:dateFrom IS NULL OR l.createdAt >= :dateFrom) AND (:dateTo IS NULL OR l.createdAt < :dateTo) " +
                   "ORDER BY l.createdAt DESC",
           countQuery = "SELECT COUNT(l) FROM AllyPatientLink l JOIN l.member m JOIN m.person p " +
                        "WHERE l.doctorPerson.id = :doctorPersonId AND l.status = 'ACTIVE' " +
                        "AND (:q IS NULL OR :q = '' OR " +
                        "cast(function('immutable_unaccent', lower(p.fullName)) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string) OR " +
                        "cast(function('immutable_unaccent', lower(p.documentNumber)) as string) LIKE cast(function('immutable_unaccent', lower(concat('%', :q, '%'))) as string)) " +
                        "AND (:dateFrom IS NULL OR l.createdAt >= :dateFrom) AND (:dateTo IS NULL OR l.createdAt < :dateTo)")
    Page<AllyPatientLink> findActiveByDoctor(@Param("doctorPersonId") Long doctorPersonId,
                                             @Param("q") String q,
                                             @Param("dateFrom") java.time.Instant dateFrom,
                                             @Param("dateTo") java.time.Instant dateTo, Pageable pageable);

    /** Unbounded variant. */
    default Page<AllyPatientLink> findActiveByDoctor(Long doctorPersonId, String q, Pageable pageable) {
        return findActiveByDoctor(doctorPersonId, q, null, null, pageable);
    }

    /** Every link of a member (active and revoked), newest first. */
    @Query("SELECT l FROM AllyPatientLink l JOIN FETCH l.doctorPerson JOIN FETCH l.createdAtAlly " +
           "WHERE l.member.id = :memberId ORDER BY l.createdAt DESC")
    List<AllyPatientLink> findByMember(@Param("memberId") Long memberId);
}
