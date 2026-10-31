package com.fenixcore.optibienestar360.modules.catalog.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.LicensingBody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicensingBodyRepository extends JpaRepository<LicensingBody, Long>, JpaSpecificationExecutor<LicensingBody> {

    Optional<LicensingBody> findByUuid(UUID uuid);

    List<LicensingBody> findAllByActiveTrueOrderByName();
}
