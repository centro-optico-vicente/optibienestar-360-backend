package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // ─── By user FK ─────────────────────────────────────────────────────────
    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByUserIdAndActiveTrue(Long userId);

    // ─── By role FK ─────────────────────────────────────────────────────────
    List<UserRole> findByRoleId(Long roleId);

    List<UserRole> findByRoleIdAndActiveTrue(Long roleId);

    boolean existsByRoleId(Long roleId);

    // ─── By the functional composite key (user_id, role_id) ─────────────────
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);
}
