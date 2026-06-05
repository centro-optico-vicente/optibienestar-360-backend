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

    /**
     * "Same service offered by different allies" — cross-ally listing for a
     * service category (e.g. every clinic that offers an 'Ophthalmology
     * consultation'). Required by ADR 0006 Rule 3 (search by every FK of
     * detail tables, even when the table carries its own uuid).
     */
    List<AllyService> findByServiceCategoryIdAndActiveTrue(Long serviceCategoryId);

    /**
     * Filtered cross-ally listing — typically called with
     * {@code ReviewStatus.APPROVED} from the public directory so unapproved
     * offerings never leak.
     */
    List<AllyService> findByServiceCategoryIdAndReviewStatusAndActiveTrue(
            Long serviceCategoryId, ReviewStatus reviewStatus);

    long countByReviewStatusIn(java.util.Collection<ReviewStatus> statuses);
}
