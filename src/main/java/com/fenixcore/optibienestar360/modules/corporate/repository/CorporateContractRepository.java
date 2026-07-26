package com.fenixcore.optibienestar360.modules.corporate.repository;

import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CorporateContractRepository
        extends JpaRepository<CorporateContract, Long>, JpaSpecificationExecutor<CorporateContract> {

    Optional<CorporateContract> findByUuid(UUID uuid);
}
