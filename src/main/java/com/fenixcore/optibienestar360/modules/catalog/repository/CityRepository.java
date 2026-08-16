package com.fenixcore.optibienestar360.modules.catalog.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.City;
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

    /** Usage count for {@code State} delete/reactivation checks — see {@code StateService}. */
    long countByState_Uuid(UUID uuid);
}
