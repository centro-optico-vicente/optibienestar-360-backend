package com.fenixcore.optibienestar360.modules.patientaccess.repository;

import com.fenixcore.optibienestar360.modules.patientaccess.entity.MedicalRecordAccessLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicalRecordAccessLogRepository extends JpaRepository<MedicalRecordAccessLog, Long> {

    /** The member's read trail, newest first. */
    @Query("SELECT a FROM MedicalRecordAccessLog a JOIN FETCH a.link l JOIN FETCH l.doctorPerson JOIN FETCH a.ally " +
           "WHERE a.member.id = :memberId ORDER BY a.accessedAt DESC")
    List<MedicalRecordAccessLog> findByMember(@Param("memberId") Long memberId, Pageable pageable);
}
