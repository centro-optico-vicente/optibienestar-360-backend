package com.fenixcore.optibienestar360.modules.organization.repository;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByUuid(UUID uuid);

    List<Organization> findByActiveTrue();

    /**
     * Single-row convenience — {@code organizations} only ever has one active
     * tenant today (V86 seed). Multi-tenant support, if it ever lands, adds a
     * proper tenant-resolution path instead of widening this method.
     */
    default Organization findSingleton() {
        return findByActiveTrue().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("organization.not_configured"));
    }
}
