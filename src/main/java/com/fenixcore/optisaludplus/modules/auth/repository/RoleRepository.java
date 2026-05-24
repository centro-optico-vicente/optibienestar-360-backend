package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    Optional<Role> findByUuid(UUID uuid);

    List<Role> findAllByActiveTrue();
}
