package com.fenixcore.optibienestar360.modules.payment.repository;

import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PaymentRepository extends JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByUuid(UUID uuid);

    /**
     * Powers {@code GET /v1/me/payments} — walks
     * {@code Payment → Membership → Member → Person} back to the User's
     * person, matching by {@code user.uuid}. Same single-JPQL shape as
     * {@code MemberRepository.findByUserUuid} so the path is index-friendly
     * (no fetch joins, the controller maps to a DTO that doesn't need the
     * nested associations).
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.active = true " +
           "  AND p.membership.member.person.id = " +
           "      (SELECT u.person.id FROM User u WHERE u.uuid = :userUuid)")
    Page<Payment> findOwnByUserUuid(@Param("userUuid") UUID userUuid, Pageable pageable);
}
