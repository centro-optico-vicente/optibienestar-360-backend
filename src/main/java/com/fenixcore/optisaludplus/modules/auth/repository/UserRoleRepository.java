package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    List<UserRole> findByRoleIdAndActiveTrue(Long roleId);

    boolean existsByRoleId(Long roleId);

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

    // ─── By the functional composite key (user_id, role_id) ─────────────────
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);
}
