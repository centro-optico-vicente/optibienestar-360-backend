package com.fenixcore.optisaludplus.modules.ally.repository;

import com.fenixcore.optisaludplus.modules.ally.entity.AllyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyUserRepository extends JpaRepository<AllyUser, Long>,
        JpaSpecificationExecutor<AllyUser> {

    Optional<AllyUser> findByUuid(UUID uuid);

    Optional<AllyUser> findByAllyIdAndUserId(Long allyId, Long userId);

    /** Listings on the user side: "what allies does this user belong to?". */
    List<AllyUser> findByUserIdAndActiveTrue(Long userId);

    /** Listings on the ally side. */
    List<AllyUser> findByAllyIdAndActiveTrue(Long allyId);

    /**
     * Active primary contact for the given ally. The V12 partial unique index
     * guarantees at most one such row (per ally where {@code is_primary AND
     * is_active}).
     */
    Optional<AllyUser> findFirstByAllyIdAndPrimaryTrueAndActiveTrue(Long allyId);
}
