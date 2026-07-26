package com.fenixcore.optibienestar360.modules.subsidy.repository;

import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface SubsidyAuditLogRepository extends JpaRepository<SubsidyAuditLog, Long> {

    /** A subsidy's audit trail, newest first ({@code GET /v1/admin/subsidies/{uuid}/log}). */
    List<SubsidyAuditLog> findBySubsidyIdOrderByCreatedAtDesc(Long subsidyId);
}
