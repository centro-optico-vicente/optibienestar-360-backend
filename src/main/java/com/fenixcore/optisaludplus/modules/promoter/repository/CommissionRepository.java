package com.fenixcore.optisaludplus.modules.promoter.repository;

import com.fenixcore.optisaludplus.modules.promoter.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface CommissionRepository extends JpaRepository<Commission, Long>,
        JpaSpecificationExecutor<Commission> {

    Optional<Commission> findByUuid(UUID uuid);

    /**
     * Used by the commission engine before INSERT — V26 partial UNIQUE
     * {@code (payment_id, promoter_id) WHERE status <> 'VOIDED'} allows
     * a fresh commission after a void, so existence check filters by
     * non-voided. The Specification path is overkill for this one call.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT (COUNT(c) > 0) FROM Commission c
            WHERE c.payment.id = :paymentId
              AND c.promoter.id = :promoterId
              AND c.status <> 'VOIDED'
            """)
    boolean existsActiveForPaymentAndPromoter(
            @org.springframework.data.repository.query.Param("paymentId") Long paymentId,
            @org.springframework.data.repository.query.Param("promoterId") Long promoterId);

    /** All commissions of a payment (for the void fan-out path). */
    List<Commission> findByPaymentId(Long paymentId);
}
