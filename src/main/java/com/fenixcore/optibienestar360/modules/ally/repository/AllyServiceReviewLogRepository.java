package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyServiceReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface AllyServiceReviewLogRepository extends JpaRepository<AllyServiceReviewLog, Long> {

    /** Chronological history (newest first) — matches the V11 index (ally_service_id, action_at DESC). */
    List<AllyServiceReviewLog> findByAllyServiceIdOrderByActionAtDesc(Long allyServiceId);
}
