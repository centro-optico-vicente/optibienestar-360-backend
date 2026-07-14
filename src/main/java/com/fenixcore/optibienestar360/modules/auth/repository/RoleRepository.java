package com.fenixcore.optibienestar360.modules.auth.repository;

import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    Optional<Role> findByUuid(UUID uuid);

    List<Role> findAllByActiveTrue();
}
