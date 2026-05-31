package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.SecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, Long> {

    Optional<SecurityPolicy> findByUuid(UUID uuid);

    Optional<SecurityPolicy> findFirstByActiveTrue();
}
