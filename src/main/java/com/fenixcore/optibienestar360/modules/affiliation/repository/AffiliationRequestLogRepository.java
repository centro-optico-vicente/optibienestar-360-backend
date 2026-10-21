package com.fenixcore.optibienestar360.modules.affiliation.repository;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface AffiliationRequestLogRepository extends JpaRepository<AffiliationRequestLog, Long> {

    /** History newest first — matches the V190 index (affiliation_request_id, action_at DESC). */
    @EntityGraph(attributePaths = {"actor", "actor.person"})
    List<AffiliationRequestLog> findByAffiliationRequestIdOrderByActionAtDescIdDesc(Long affiliationRequestId);
}
