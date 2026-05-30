package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.AllyType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllyTypeRepository extends JpaRepository<AllyType, Long> {

    Optional<AllyType> findByUuid(UUID uuid);

    Optional<AllyType> findByCode(String code);

    List<AllyType> findAllByActiveTrueOrderByName();
}
