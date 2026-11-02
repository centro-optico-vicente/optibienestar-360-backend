package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentLineRepository extends JpaRepository<PaymentLine, Long> {

    List<PaymentLine> findByPaymentUuid(UUID paymentUuid);

    /**
     * Lines of the membership's approved inscription payments ({@code IN},
     * category {@code INSCRIPTION_FEE}), oldest payment first and, inside a
     * payment, in entry order — the first row is the method the inscription
     * was paid with ("Modalidad de pago" of the B2C dossier, hub ADR 0031 §14).
     * Callers pass {@code PageRequest.of(0, 1)}.
     */
    @Query("SELECT l FROM PaymentLine l JOIN FETCH l.paymentType JOIN l.payment p " +
           "WHERE p.membership.id = :membershipId " +
           "  AND p.direction = 'IN' " +
           "  AND p.paymentType.code = 'INSCRIPTION_FEE' " +
           "  AND p.status = 'APPROVED' " +
           "  AND p.active = true AND l.active = true " +
           "ORDER BY p.paymentDate ASC, p.id ASC, l.id ASC")
    List<PaymentLine> findInscriptionLines(@Param("membershipId") Long membershipId, Pageable pageable);
}
