package com.fenixcore.optisaludplus.modules.catalog.repository;

import com.fenixcore.optisaludplus.modules.catalog.entity.MedicalSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicalSpecialtyRepository extends JpaRepository<MedicalSpecialty, Long>, JpaSpecificationExecutor<MedicalSpecialty> {

    Optional<MedicalSpecialty> findByUuid(UUID uuid);

    Optional<MedicalSpecialty> findByCode(String code);

    List<MedicalSpecialty> findAllByActiveTrueOrderByName();
}
