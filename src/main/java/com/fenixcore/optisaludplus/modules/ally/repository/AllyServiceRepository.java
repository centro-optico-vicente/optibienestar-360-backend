package com.fenixcore.optisaludplus.modules.ally.repository;

import com.fenixcore.optisaludplus.modules.ally.entity.AllyService;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyService.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyServiceRepository extends JpaRepository<AllyService, Long>,
        JpaSpecificationExecutor<AllyService> {

    Optional<AllyService> findByUuid(UUID uuid);

    List<AllyService> findByAllyIdAndActiveTrue(Long allyId);

    List<AllyService> findByAllyIdAndReviewStatus(Long allyId, ReviewStatus reviewStatus);

    long countByReviewStatusIn(java.util.Collection<ReviewStatus> statuses);
}
