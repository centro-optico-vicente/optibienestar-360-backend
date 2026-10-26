package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyProductRepository extends JpaRepository<AllyProduct, Long>,
        JpaSpecificationExecutor<AllyProduct> {

    Optional<AllyProduct> findByUuid(UUID uuid);

    /**
     * Overrides the inherited spec-paged {@code findAll} to eager-fetch the
     * to-one associations the public projections read
     * ({@link com.fenixcore.optibienestar360.modules.ally.dto.PublicProductListItemDto}
     * flattens the parent ally's identity), avoiding an N+1 over the page rows.
     * All fetched paths are {@code @ManyToOne}, so in-DB pagination is safe (no
     * collection fetch → no HHH000104 in-memory paging). {@code ally.allyTypes}
     * is deliberately NOT in this graph — it became a {@code @ManyToMany}
     * collection in V157, and fetching it here would force in-memory paging;
     * the mapper reads it lazily instead (safe: the caller is
     * {@code @Transactional(readOnly = true)}). This method has no
     * other callers today — the admin/propose services use named finders — so
     * the eager graph does not over-fetch anywhere else.
     */
    @Override
    @EntityGraph(attributePaths = {"ally", "ally.city", "product", "product.productCategory"})
    Page<AllyProduct> findAll(Specification<AllyProduct> spec, Pageable pageable);

    /**
     * Unpaged variant with the same eager graph — backs the public product
     * detail, which lists every ally offering one product (a handful of rows)
     * and reads each ally's identity.
     */
    @Override
    @EntityGraph(attributePaths = {"ally", "ally.city", "product", "product.productCategory"})
    List<AllyProduct> findAll(Specification<AllyProduct> spec);

    List<AllyProduct> findByAllyIdAndActiveTrue(Long allyId, Sort sort);

    /** One offer per (ally, product) — mirrors {@code uq_ally_products_ally_product} (V197). */
    boolean existsByAllyIdAndProductId(Long allyId, Long productId);

    /** Usage check for {@code AlliesService.countUsages} — ALL rows (active + inactive). */
    long countByAllyId(Long allyId);

    List<AllyProduct> findByAllyIdAndReviewStatus(Long allyId, ReviewStatus reviewStatus);

    /**
     * "Same product offered by different allies" — cross-ally listing for a
     * catalog product (e.g. every clinic that offers an 'Ophthalmology
     * consultation'). Required by ADR 0006 Rule 3 (search by every FK of
     * detail tables, even when the table carries its own uuid).
     */
    List<AllyProduct> findByProductIdAndActiveTrue(Long productId);

    /** Usage count for {@code ProductsService} — offers pointing at a product, active + inactive. */
    long countByProductId(Long productId);

    long countByReviewStatusIn(java.util.Collection<ReviewStatus> statuses);

    /** Usage count for {@code ProductCategory} delete/reactivation checks — see {@code ProductCategoryService}. */
    long countByProduct_ProductCategory_Uuid(UUID uuid);
}
