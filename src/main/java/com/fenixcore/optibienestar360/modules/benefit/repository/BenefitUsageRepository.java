package com.fenixcore.optibienestar360.modules.benefit.repository;

import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
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

    /** Usage check for {@code AlliesService.countUsages} — ALL rows (active + inactive). */
    long countByAllyId(Long allyId);

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

    /**
     * Powers {@code GET /v1/me/usage-history} (vertical-9) — every benefit usage
     * on the caller's own membership history. Walks {@code user.person →
     * member.person → membership → benefit_usage} so a member sees their whole
     * consumption feed regardless of which ally registered it. {@code bu.active}
     * filters reversed / soft-deleted rows; the caller's {@link Pageable}
     * supplies the sort (default {@code usageDate DESC}).
     */
    @Query("""
            SELECT bu FROM BenefitUsage bu
            WHERE bu.active = true
              AND bu.membership.member.person.id =
                  (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)
            """)
    Page<BenefitUsage> findByMemberUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);
}
