package com.fenixcore.optibienestar360.modules.member.repository;

import com.fenixcore.optibienestar360.modules.member.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByUuid(UUID uuid);

    /**
     * Per V19 the relationship is 1:1 with persons via UNIQUE(person_id), so
     * this returns at most one row. Backs the {@code GET /medical-record}
     * sub-resource and the upsert flow.
     */
    Optional<MedicalRecord> findByPersonId(Long personId);

    boolean existsByPersonId(Long personId);
}
