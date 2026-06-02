package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long>, JpaSpecificationExecutor<DocumentType> {

    Optional<DocumentType> findByUuid(UUID uuid);

    Optional<DocumentType> findByCode(String code);

    List<DocumentType> findAllByActiveTrueOrderByName();
}
