package com.fenixcore.optibienestar360.modules.catalog.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.MaritalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaritalStatusRepository extends JpaRepository<MaritalStatus, Long>, JpaSpecificationExecutor<MaritalStatus> {

    Optional<MaritalStatus> findByUuid(UUID uuid);

    Optional<MaritalStatus> findByCode(String code);

    List<MaritalStatus> findAllByActiveTrueOrderByName();
}
