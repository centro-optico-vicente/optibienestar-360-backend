package com.fenixcore.optibienestar360.modules.auth.repository;

import com.fenixcore.optibienestar360.modules.auth.entity.PermissionDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PermissionDomainRepository extends JpaRepository<PermissionDomain, Long> {

    Optional<PermissionDomain> findByUuid(UUID uuid);

    Optional<PermissionDomain> findByCode(String code);

    List<PermissionDomain> findAllByActiveTrueOrderByDisplayOrder();
}
