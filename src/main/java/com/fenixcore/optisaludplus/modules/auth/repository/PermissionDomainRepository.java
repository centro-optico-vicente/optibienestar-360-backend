package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.PermissionDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionDomainRepository extends JpaRepository<PermissionDomain, Long> {

    Optional<PermissionDomain> findByUuid(UUID uuid);

    Optional<PermissionDomain> findByCode(String code);

    List<PermissionDomain> findAllByActiveTrueOrderByDisplayOrder();
}
