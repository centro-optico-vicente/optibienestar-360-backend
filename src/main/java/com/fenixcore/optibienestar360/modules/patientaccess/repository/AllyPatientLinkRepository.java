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

    /** The doctor's patients ("Mis pacientes"), newest link first. */
    @Query(value = "SELECT l FROM AllyPatientLink l JOIN FETCH l.member m JOIN FETCH m.person " +
                   "WHERE l.doctorPerson.id = :doctorPersonId AND l.status = 'ACTIVE' ORDER BY l.createdAt DESC",
           countQuery = "SELECT COUNT(l) FROM AllyPatientLink l " +
                        "WHERE l.doctorPerson.id = :doctorPersonId AND l.status = 'ACTIVE'")
    Page<AllyPatientLink> findActiveByDoctor(@Param("doctorPersonId") Long doctorPersonId, Pageable pageable);

    /** Every link of a member (active and revoked), newest first. */
    @Query("SELECT l FROM AllyPatientLink l JOIN FETCH l.doctorPerson JOIN FETCH l.createdAtAlly " +
           "WHERE l.member.id = :memberId ORDER BY l.createdAt DESC")
    List<AllyPatientLink> findByMember(@Param("memberId") Long memberId);
}
