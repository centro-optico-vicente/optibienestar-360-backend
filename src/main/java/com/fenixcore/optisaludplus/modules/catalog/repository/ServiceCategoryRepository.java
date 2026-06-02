package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long>, JpaSpecificationExecutor<ServiceCategory> {

    Optional<ServiceCategory> findByUuid(UUID uuid);

    Optional<ServiceCategory> findByCode(String code);

    List<ServiceCategory> findAllByActiveTrueOrderByName();
}
