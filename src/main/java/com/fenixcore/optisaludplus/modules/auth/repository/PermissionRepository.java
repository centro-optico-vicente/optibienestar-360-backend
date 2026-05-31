package com.fenixcore.optisaludplus.modules.auth.repository;

import com.fenixcore.optisaludplus.modules.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByUuid(UUID uuid);

    Optional<Permission> findByName(String name);

    List<Permission> findAllByActiveTrue();

    /**
     * Loads active permissions together with their domain in a single
     * query (JOIN FETCH) and returns them ordered as the admin panel
     * expects: first by domain {@code displayOrder}, then by permission
     * {@code description}. Avoids the N+1 that would happen if the
     * service iterated over LAZY-loaded domains.
     */
    @Query("""
            SELECT p
            FROM Permission p
            JOIN FETCH p.domain d
            WHERE p.active = true AND d.active = true
            ORDER BY d.displayOrder, p.description
            """)
    List<Permission> findAllActiveOrderedForCatalog();
}
