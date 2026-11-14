package com.fenixcore.optibienestar360.modules.auth.repository;

import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // ─── By user FK ─────────────────────────────────────────────────────────
    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByUserIdAndActiveTrue(Long userId);

    // ─── By role FK ─────────────────────────────────────────────────────────
    List<UserRole> findByRoleId(Long roleId);

    List<UserRole> findByRoleIdAndActiveTrue(Long roleId, Sort sort);

    boolean existsByRoleId(Long roleId);

    /** Usage check for {@code RoleService.countUsages} — ALL rows (active + inactive), see that method's Javadoc. */
    long countByRoleId(Long roleId);

    /** Usage check for {@code UserService.countUsages} — ALL rows (active + inactive). */
    long countByUserId(Long userId);

    /**
     * Projection for the token-staleness fan-out: returns just the user UUIDs of
     * every active assignment to {@code roleId}. Used by {@code RoleService} to
     * call {@code blacklistService.markUserInvalidatedNow(uuid)} for each
     * affected user when a role's permissions change. Avoids the N+1 of
     * iterating {@code findByRoleIdAndActiveTrue} and walking {@code ur.user.uuid}
     * lazily, which would issue one extra query per row.
     */
    @Query("SELECT ur.user.uuid FROM UserRole ur " +
           "WHERE ur.role.id = :roleId AND ur.active = true")
    List<UUID> findActiveUserUuidsByRoleId(@Param("roleId") Long roleId);

    // ─── Notification fan-out ───────────────────────────────────────────────

    /**
     * Ids of the active users that currently hold (effective, not expired) any
     * of the active roles named {@code roleNames}. Used by
     * {@code UserNotificationPublisher} to resolve "every user with role X" at
     * publish time.
     */
    @Query("SELECT DISTINCT ur.user.id FROM UserRole ur " +
           "WHERE ur.active = true AND (ur.expiresAt IS NULL OR ur.expiresAt > :now) " +
           "AND ur.user.active = true AND ur.user.status = 'ACTIVE' " +
           "AND ur.role.active = true AND ur.role.name IN :roleNames")
    List<Long> findEffectiveUserIdsByRoleNames(@Param("roleNames") Collection<String> roleNames,
                                               @Param("now") Instant now);

    /**
     * Ids of the active users that currently hold an effective role granting any
     * of the permissions named {@code permissionNames}. Same purpose as
     * {@link #findEffectiveUserIdsByRoleNames}, for "every user who can do X".
     */
    @Query("SELECT DISTINCT ur.user.id FROM UserRole ur JOIN ur.role r JOIN r.permissions p " +
           "WHERE ur.active = true AND (ur.expiresAt IS NULL OR ur.expiresAt > :now) " +
           "AND ur.user.active = true AND ur.user.status = 'ACTIVE' " +
           "AND r.active = true AND p.active = true AND p.name IN :permissionNames")
    List<Long> findEffectiveUserIdsByPermissionNames(@Param("permissionNames") Collection<String> permissionNames,
                                                     @Param("now") Instant now);

    // ─── By the functional composite key (user_id, role_id) ─────────────────
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);
}
