package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface AllyAgreementRepository extends JpaRepository<AllyAgreement, Long>,
        JpaSpecificationExecutor<AllyAgreement> {

    Optional<AllyAgreement> findByUuid(UUID uuid);

    List<AllyAgreement> findByAllyIdAndActiveTrue(Long allyId, Sort sort);

    /** Usage check for {@code AlliesService.countUsages} — ALL rows (active + inactive). */
    long countByAllyId(Long allyId);

    List<AllyAgreement> findByAllyIdAndStatus(Long allyId, String status);

    /**
     * Agreements in force ({@code is_active} + the given lifecycle status) of
     * the given allies, with their standard terms version fetched — backs the
     * ALIADO branch of {@code TermsAcceptanceService.pendingForUser}.
     */
    @Query("SELECT a FROM AllyAgreement a " +
           "LEFT JOIN FETCH a.termsVersion " +
           "WHERE a.ally.id IN :allyIds " +
           "  AND a.active = true " +
           "  AND a.status = :status")
    List<AllyAgreement> findInForceWithTermsVersionByAllyIds(@Param("allyIds") Collection<Long> allyIds,
                                                            @Param("status") String status);
}
