package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoterRankRepository extends JpaRepository<PromoterRank, Long>, JpaSpecificationExecutor<PromoterRank> {

    Optional<PromoterRank> findByUuid(UUID uuid);

    Optional<PromoterRank> findByCode(String code);

    List<PromoterRank> findAllByActiveTrueOrderByHierarchyLevel();

    boolean existsByHierarchyLevel(int hierarchyLevel);

    /**
     * Used by {@code PromoterHierarchyService.changeRank} to tell whether a
     * target rank is the top of the chain — if no active rank has a higher
     * {@code hierarchyLevel}, a promoter moved to it needs no supervisor.
     */
    boolean existsByHierarchyLevelGreaterThanAndActiveTrue(int hierarchyLevel);

    /**
     * The immediate superior rank of {@code hierarchyLevel} — the active
     * rank with the smallest {@code hierarchyLevel} that is still greater
     * than it. Empty means {@code hierarchyLevel} is already the top of the
     * chain. Powers {@code PromoterHierarchyService.eligibleSupervisorOptions}'s
     * default "only the immediate next rank up" scope.
     */
    Optional<PromoterRank> findFirstByHierarchyLevelGreaterThanAndActiveTrueOrderByHierarchyLevelAsc(int hierarchyLevel);

    /**
     * Direct children of {@code parent} (V111) — used by {@code
     * PromoterRankService.reorder} to re-validate the parent/child invariant
     * of every rank that has the just-moved rank as its {@code parentRank}.
     */
    List<PromoterRank> findAllByParentRankAndActiveTrue(PromoterRank parent);
}
