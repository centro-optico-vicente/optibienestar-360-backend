package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyRepository extends JpaRepository<Ally, Long>, JpaSpecificationExecutor<Ally> {

    Optional<Ally> findByUuid(UUID uuid);

    Optional<Ally> findByTaxDocumentTypeAndTaxDocumentNumber(String taxDocumentType,
                                                             String taxDocumentNumber);

    boolean existsByTaxDocumentTypeAndTaxDocumentNumber(String taxDocumentType,
                                                        String taxDocumentNumber);
}
