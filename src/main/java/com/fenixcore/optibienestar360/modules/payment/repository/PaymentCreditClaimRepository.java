package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCreditClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentCreditClaimRepository extends JpaRepository<PaymentCreditClaim, Long> {

    Optional<PaymentCreditClaim> findByUuid(UUID uuid);

    /** The single OPEN claim of a payment, if any (V205 partial unique index). */
    @Query("SELECT c FROM PaymentCreditClaim c WHERE c.payment.id = :paymentId AND c.claimStatus = 'OPEN' AND c.active = true")
    Optional<PaymentCreditClaim> findOpenByPaymentId(@Param("paymentId") Long paymentId);

    /** Amount of a payment frozen by its OPEN claim (0 when none). */
    @Query("SELECT COALESCE(SUM(c.claimedAmount), 0) FROM PaymentCreditClaim c "
            + "WHERE c.payment.id = :paymentId AND c.claimStatus = 'OPEN' AND c.active = true")
    BigDecimal frozenAmountOfPayment(@Param("paymentId") Long paymentId);

    Page<PaymentCreditClaim> findByActiveTrue(Pageable pageable);

    Page<PaymentCreditClaim> findByActiveTrueAndClaimStatus(String claimStatus, Pageable pageable);
}
