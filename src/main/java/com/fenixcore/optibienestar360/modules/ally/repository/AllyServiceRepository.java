package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Overrides the inherited spec-paged {@code findAll} to eager-fetch the
     * to-one associations the public projections read
     * ({@link com.fenixcore.optibienestar360.modules.ally.dto.PublicServiceListItemDto}
     * flattens the parent ally's identity), avoiding an N+1 over the page rows.
     * All fetched paths are {@code @ManyToOne}, so in-DB pagination is safe (no
     * collection fetch → no HHH000104 in-memory paging). This method has no
     * other callers today — the admin/propose services use named finders — so
     * the eager graph does not over-fetch anywhere else.
     */
    @Override
    @EntityGraph(attributePaths = {"ally", "ally.allyType", "ally.city", "serviceCategory"})
    Page<AllyService> findAll(Specification<AllyService> spec, Pageable pageable);

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

    /** Usage count for {@code ServiceCategory} delete/reactivation checks — see {@code ServiceCategoryService}. */
    long countByServiceCategory_Uuid(UUID uuid);
}
