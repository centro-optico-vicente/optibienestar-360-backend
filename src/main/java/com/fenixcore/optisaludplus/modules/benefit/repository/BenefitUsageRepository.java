package com.fenixcore.optisaludplus.modules.benefit.repository;

import com.fenixcore.optisaludplus.modules.benefit.entity.BenefitUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface BenefitUsageRepository extends JpaRepository<BenefitUsage, Long>,
        JpaSpecificationExecutor<BenefitUsage> {

    Optional<BenefitUsage> findByUuid(UUID uuid);

    /**
     * Powers {@code GET /v1/ally/usage-history} — every benefit usage
     * registered at any ally the current user is an active operator on.
     * The {@code AllyUser} pivot (V12) is N:M, so a single user can see
     * usages across multiple allies; the subquery handles that without
     * forcing the controller to enumerate them.
     *
     * <p>{@code bu.active = true} filters reversed / soft-deleted rows;
     * {@code au.active = true} filters revoked memberships in the
     * {@code AllyUser} pivot. The composite index
     * {@code idx_benefit_usages_ally_date (ally_id, usage_date DESC)} from
     * V24 backs the sort.</p>
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            WHERE bu.active = true
              AND bu.ally.id IN (
                  SELECT au.ally.id FROM AllyUser au
                  WHERE au.user.uuid = :userUuid AND au.active = true
              )
            """)
    Page<BenefitUsage> findByAllyUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);
}
