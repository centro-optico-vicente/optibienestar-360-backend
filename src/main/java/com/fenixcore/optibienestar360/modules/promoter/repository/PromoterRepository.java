package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterRepository extends JpaRepository<Promoter, Long>,
        JpaSpecificationExecutor<Promoter> {

    Optional<Promoter> findByUuid(UUID uuid);

    /** Current (as-of-now) direct subordinates via the live pointer — the fast path for {@code PromoterHierarchyService}. */
    List<Promoter> findBySupervisorId(Long supervisorId);

    /** Natural-key lookup — services that need "the INSTITUCION promoter" resolve by code. */
    Optional<Promoter> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    /** Cross-table uniqueness pre-check — see V27 referrals deferred-decision item on collisions. */
    boolean existsByUserId(Long userId);

    /** Usage check for {@code UserService.countUsages} — ALL rows (active + inactive). */
    long countByUserId(Long userId);

    /**
     * Resolve the promoter of the JWT-authenticated user for the self-service
     * dashboard {@code GET /v1/promoter/me}. Backed by the V25 partial UNIQUE
     * {@code (user_id) WHERE user_id IS NOT NULL AND is_active=TRUE}, so at most
     * one active row matches.
     */
    @Query("SELECT p FROM Promoter p WHERE p.active = true AND p.user.uuid = :userUuid")
    Optional<Promoter> findActiveByUserUuid(@Param("userUuid") UUID userUuid);
}
