package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.MaritalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaritalStatusRepository extends JpaRepository<MaritalStatus, Long> {

    Optional<MaritalStatus> findByUuid(UUID uuid);

    Optional<MaritalStatus> findByCode(String code);

    List<MaritalStatus> findAllByActiveTrueOrderByName();
}
