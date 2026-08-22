package com.fenixcore.optibienestar360.core.audit.repository;

import com.fenixcore.optibienestar360.core.audit.entity.ReportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportAuditLogRepository
        extends JpaRepository<ReportAuditLog, Long>, JpaSpecificationExecutor<ReportAuditLog> {

    Optional<ReportAuditLog> findByUuid(UUID uuid);
}
