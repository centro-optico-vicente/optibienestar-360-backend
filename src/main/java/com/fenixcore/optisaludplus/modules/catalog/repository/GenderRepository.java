package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.Gender;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GenderRepository extends JpaRepository<Gender, Long> {

    Optional<Gender> findByUuid(UUID uuid);

    Optional<Gender> findByCode(String code);

    List<Gender> findAllByActiveTrueOrderByName();
}
