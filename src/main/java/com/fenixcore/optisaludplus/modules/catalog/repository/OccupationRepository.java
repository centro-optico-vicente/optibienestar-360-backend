package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.Occupation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OccupationRepository extends JpaRepository<Occupation, Long> {

    Optional<Occupation> findByUuid(UUID uuid);

    List<Occupation> findAllByActiveTrueOrderByName();
}
