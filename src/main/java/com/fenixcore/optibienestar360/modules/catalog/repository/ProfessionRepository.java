package com.fenixcore.optibienestar360.modules.catalog.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfessionRepository extends JpaRepository<Profession, Long>, JpaSpecificationExecutor<Profession> {

    Optional<Profession> findByUuid(UUID uuid);

    Optional<Profession> findByCode(String code);

    List<Profession> findAllByActiveTrueOrderByName();

    /** A profession with sub-specialties cannot become one itself (max two levels, V213). */
    boolean existsByParent_Id(Long parentId);
}
