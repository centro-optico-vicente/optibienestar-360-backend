package com.fenixcore.optibienestar360.modules.product.repository;

import com.fenixcore.optibienestar360.modules.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByUuid(UUID uuid);

    boolean existsByCodeIgnoreCase(String code);

    /** Both associations are {@code @ManyToOne}, so in-DB pagination stays safe. */
    @Override
    @EntityGraph(attributePaths = {"productCategory", "ownerAlly"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Usage count for {@code ProductCategory} delete/reactivation checks — see {@code ProductCategoryService}. */
    long countByProductCategory_Uuid(UUID uuid);

    /** Usage check for {@code AlliesService.countUsages} — the ally's exclusive products, active + inactive. */
    long countByOwnerAllyId(Long allyId);
}
