package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyUserRepository extends JpaRepository<AllyUser, Long>,
        JpaSpecificationExecutor<AllyUser> {

    Optional<AllyUser> findByUuid(UUID uuid);

    Optional<AllyUser> findByAllyIdAndUserId(Long allyId, Long userId);

    /** Usage check for {@code AlliesService.countUsages} — ALL rows (active + inactive). */
    long countByAllyId(Long allyId);

    /** Usage check for {@code UserService.countUsages} — ALL rows (active + inactive). */
    long countByUserId(Long userId);

    /** Listings on the user side: "what allies does this user belong to?". */
    List<AllyUser> findByUserIdAndActiveTrue(Long userId, Sort sort);

    /** Listings on the ally side. */
    List<AllyUser> findByAllyIdAndActiveTrue(Long allyId, Sort sort);

    /**
     * Active primary contact for the given ally. The V12 partial unique index
     * guarantees at most one such row (per ally where {@code is_primary AND
     * is_active}).
     */
    Optional<AllyUser> findFirstByAllyIdAndPrimaryTrueAndActiveTrue(Long allyId);

    /**
     * Membership lookup by the natural keys (UUIDs) of the parent ally and
     * user. Backs the ally-side {@code POST /v1/aliado/services} flow where
     * we get a user UUID from the JWT + an ally UUID from the request body
     * and need to verify the actor has an active membership before letting
     * them propose a service.
     */
    @Query("SELECT au FROM AllyUser au " +
           "WHERE au.ally.uuid = :allyUuid " +
           "  AND au.user.uuid = :userUuid " +
           "  AND au.active = true")
    Optional<AllyUser> findActiveByAllyUuidAndUserUuid(@Param("allyUuid") UUID allyUuid,
                                                      @Param("userUuid") UUID userUuid);

    /**
     * Backs {@code GET /v1/me/allies}: every ally the given user can operate
     * on, resolved from the JWT subject alone. Fetch-joins the parent ally and
     * its type so the portal picker renders without an N+1 walk over the
     * lazy {@code ally} relation.
     *
     * <p>Soft-deleted allies are excluded even when the pivot row is still
     * active — a membership on a deleted ally is not operable, and returning
     * it would let the portal POST usages against it.</p>
     *
     * <p>Unordered by design; {@link
     * com.fenixcore.optibienestar360.modules.ally.service.MyAlliesService}
     * applies the primary-first ordering in Java.</p>
     */
    @Query("SELECT DISTINCT au FROM AllyUser au " +
           "JOIN FETCH au.ally a " +
           "LEFT JOIN FETCH a.allyTypes " +
           "WHERE au.user.uuid = :userUuid " +
           "  AND au.active = true " +
           "  AND a.active = true")
    List<AllyUser> findActiveByUserUuid(@Param("userUuid") UUID userUuid);
}
