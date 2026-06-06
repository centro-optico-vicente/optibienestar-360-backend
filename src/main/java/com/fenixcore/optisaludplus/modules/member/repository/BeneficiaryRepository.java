package com.fenixcore.optisaludplus.modules.member.repository;

import com.fenixcore.optisaludplus.modules.member.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long>,
        JpaSpecificationExecutor<Beneficiary> {

    Optional<Beneficiary> findByUuid(UUID uuid);

    List<Beneficiary> findByMemberIdAndActiveTrue(Long memberId);

    /** Where else is this person a beneficiary? (validator + digital card flows). */
    List<Beneficiary> findByPersonIdAndActiveTrue(Long personId);

    /** Backs the readmission flow — UNIQUE(member_id, person_id) means we reactivate, never duplicate. */
    Optional<Beneficiary> findByMemberIdAndPersonId(Long memberId, Long personId);

    long countByMemberIdAndActiveTrue(Long memberId);
}
