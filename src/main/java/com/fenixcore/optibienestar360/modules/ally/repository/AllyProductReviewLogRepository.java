package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyProductReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface AllyProductReviewLogRepository extends JpaRepository<AllyProductReviewLog, Long> {

    /** Chronological history (newest first) — matches the V11 index (ally_product_id, action_at DESC). */
    List<AllyProductReviewLog> findByAllyProductIdOrderByActionAtDesc(Long allyProductId);
}
