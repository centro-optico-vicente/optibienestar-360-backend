package com.fenixcore.optibienestar360.modules.system.repository;

import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByUuid(UUID uuid);

    Optional<SystemConfig> findFirstByActiveTrue();
}
