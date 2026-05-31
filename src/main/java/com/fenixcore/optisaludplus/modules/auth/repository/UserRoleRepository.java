package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserIdAndActiveTrue(Long userId);
}
