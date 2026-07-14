package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyAgreementRepository extends JpaRepository<AllyAgreement, Long>,
        JpaSpecificationExecutor<AllyAgreement> {

    Optional<AllyAgreement> findByUuid(UUID uuid);

    List<AllyAgreement> findByAllyIdAndActiveTrue(Long allyId);

    List<AllyAgreement> findByAllyIdAndStatus(Long allyId, String status);
}
