package com.fenixcore.optibienestar360.core.audit.repository;

import com.fenixcore.optibienestar360.core.audit.entity.AuditEntityConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditEntityConfigRepository extends JpaRepository<AuditEntityConfig, Long> {

    Optional<AuditEntityConfig> findByEntityKey(String entityKey);

    List<AuditEntityConfig> findAllByOrderByDisplayNameAsc();
}
