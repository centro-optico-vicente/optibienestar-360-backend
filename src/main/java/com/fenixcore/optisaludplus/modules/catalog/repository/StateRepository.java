package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StateRepository extends JpaRepository<State, Long> {

    Optional<State> findByUuid(UUID uuid);

    Optional<State> findByCode(String code);

    List<State> findAllByActiveTrueOrderByName();

    List<State> findByCountry_IsoCodeAndActiveTrueOrderByName(String countryIsoCode);
}
