package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CityRepository extends JpaRepository<City, Long>, JpaSpecificationExecutor<City> {

    Optional<City> findByUuid(UUID uuid);

    List<City> findAllByActiveTrueOrderByName();

    List<City> findByState_CodeAndActiveTrueOrderByName(String stateCode);

    List<City> findByState_UuidAndActiveTrueOrderByName(UUID stateUuid);
}
