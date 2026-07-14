package com.fenixcore.optibienestar360.modules.catalog.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllyTypeRepository extends JpaRepository<AllyType, Long>, JpaSpecificationExecutor<AllyType> {

    Optional<AllyType> findByUuid(UUID uuid);

    Optional<AllyType> findByCode(String code);

    List<AllyType> findAllByActiveTrueOrderByName();
}
