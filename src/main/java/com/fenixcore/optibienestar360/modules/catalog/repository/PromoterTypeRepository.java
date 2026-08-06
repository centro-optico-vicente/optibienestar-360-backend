package com.fenixcore.optibienestar360.modules.catalog.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoterTypeRepository extends JpaRepository<PromoterType, Long>, JpaSpecificationExecutor<PromoterType> {

    Optional<PromoterType> findByUuid(UUID uuid);

    Optional<PromoterType> findByCode(String code);

    List<PromoterType> findAllByActiveTrueOrderByName();
}
